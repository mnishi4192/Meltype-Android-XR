// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
// Android JNI adapter for the Meltype.Native NativeAOT C ABI (meltype_ime_*).
#include <jni.h>
#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <new>
#include <mutex>
#include <string>
#include <vector>

extern "C" {
int meltype_android_init_with_zenzai(unsigned char* (*mozc_request)(unsigned char*),
                                    unsigned char* (*zenzai_request)(unsigned char*));
void meltype_set_conversion_ready(void (*notify)());
void* meltype_ime_create();
unsigned char* meltype_ime_handle(void* host, unsigned char* json);
void meltype_ime_destroy(void* host);
void meltype_free(unsigned char* ptr);
}

namespace {
std::mutex init_mutex;
std::string data_dir;
// MELTYPE_DATA_DIR/AppPaths are process-global. A failed Core init cannot safely be retried
// with a different directory after the native runtime may have read the old one.
bool init_attempted = false;
// The Core calls these from its converter thread, which the JVM does not know.
struct CallbackBindings {
    JavaVM* vm;
    jobject receiver;
    jmethodID mozc;
    jmethodID zenzai;
    jmethodID ready;
};
CallbackBindings binding_storage{};
std::atomic<const CallbackBindings*> bindings{nullptr};

// No Java exception or potentially user-dependent exception message crosses the C ABI.
void discard_callback_exception(JNIEnv* env) {
    if (env->ExceptionCheck()) env->ExceptionClear();
}

struct CallbackEnvironment {
    JNIEnv* env = nullptr;
    bool attached = false;
    JavaVM* vm;
    explicit CallbackEnvironment(JavaVM* vm) : vm(vm) {
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK) attached = true;
        }
    }
    ~CallbackEnvironment() { if (attached) vm->DetachCurrentThread(); }
};

jbyteArray new_utf8_array(JNIEnv* env, const unsigned char* text) {
    if (!text) return nullptr;
    const auto size = std::strlen(reinterpret_cast<const char*>(text));
    if (size > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    auto bytes = env->NewByteArray(static_cast<jsize>(size));
    if (bytes && size > 0)
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(text));
    return bytes;
}

unsigned char* malloc_java_bytes(JNIEnv* env, jbyteArray result) {
    if (env->ExceptionCheck()) { discard_callback_exception(env); return nullptr; }
    if (!result) return nullptr;
    const jsize length = env->GetArrayLength(result);
    auto* buffer = reinterpret_cast<unsigned char*>(std::malloc(static_cast<size_t>(length) + 1u));
    if (!buffer) return nullptr;
    if (length) env->GetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(buffer));
    buffer[length] = 0;
    if (env->ExceptionCheck()) { discard_callback_exception(env); std::free(buffer); return nullptr; }
    return buffer;
}

unsigned char* request_bytes(const CallbackBindings* callback, jmethodID method, unsigned char* request) {
    CallbackEnvironment jni(callback->vm);
    if (!jni.env) return nullptr;
    auto* env = jni.env;
    const auto r = new_utf8_array(env, request);
    if (env->ExceptionCheck() || !r) {
        discard_callback_exception(env);
        if (r) env->DeleteLocalRef(r);
        return nullptr;
    }
    auto result = static_cast<jbyteArray>(env->CallObjectMethod(callback->receiver, method, r));
    auto* answer = malloc_java_bytes(env, result);
    if (result) env->DeleteLocalRef(result);
    env->DeleteLocalRef(r);
    return answer;
}

// One Mozc helper request line in, one answer line out (native/mozc/meltype_mozc_helper.cc).
unsigned char* mozc_request(unsigned char* request) {
    const auto* callback = bindings.load(std::memory_order_acquire);
    return callback ? request_bytes(callback, callback->mozc, request) : nullptr;
}

// The official Zenzai prompt and completion, as UTF-8 without a modified-JNI encoding step.
unsigned char* zenzai_request(unsigned char* request) {
    const auto* callback = bindings.load(std::memory_order_acquire);
    return callback ? request_bytes(callback, callback->zenzai, request) : nullptr;
}

void conversion_ready() {
    const auto* callback = bindings.load(std::memory_order_acquire);
    if (!callback) return;
    CallbackEnvironment jni(callback->vm);
    if (!jni.env) return;
    jni.env->CallVoidMethod(callback->receiver, callback->ready);
    discard_callback_exception(jni.env);
}

// Java Modified UTF-8 is not ordinary UTF-8 for NUL and supplementary planes.
// Byte arrays preserve the exact UTF-8 bytes produced by Kotlin.
std::vector<unsigned char> read_utf8(JNIEnv* env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize size = env->GetArrayLength(bytes);
    std::vector<unsigned char> result(static_cast<size_t>(size) + 1u, 0);
    if (size > 0) env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(result.data()));
    return result;
}

jbyteArray return_utf8(JNIEnv* env, unsigned char* text) {
    if (!text) return nullptr;
    const size_t size = std::strlen(reinterpret_cast<const char*>(text));
    if (size > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        meltype_free(text);
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
    if (result && size > 0) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(size),
                                reinterpret_cast<const jbyte*>(text));
    }
    meltype_free(text);
    return result;
}

void* as_handle(jlong handle) { return reinterpret_cast<void*>(static_cast<uintptr_t>(handle)); }
} // namespace

// One process-wide data directory, set before first access to Meltype.Config.AppPaths.
// Never switch the directory after the Core has initialized its static config.
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_ninjin0802_keyboard_app_core_NativeApi_init(
        JNIEnv* env, jclass, jbyteArray directory, jobject callbacks) {
    if (!directory || !callbacks) return JNI_FALSE;
    const auto path = read_utf8(env, directory);
    if (env->ExceptionCheck() || path.size() <= 1) return JNI_FALSE;
    const std::string requested(reinterpret_cast<const char*>(path.data()), path.size() - 1);
    if (requested.empty() || requested.find('\0') != std::string::npos) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(init_mutex);
    if (!data_dir.empty()) return data_dir == requested && env->IsSameObject(binding_storage.receiver, callbacks)
            ? JNI_TRUE : JNI_FALSE;
    if (init_attempted) return JNI_FALSE;
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK) return JNI_FALSE;
    auto clazz = env->GetObjectClass(callbacks);
    if (!clazz) return JNI_FALSE;
    const auto mozc = env->GetMethodID(clazz, "request", "([B)[B");
    const auto zenzai = mozc ? env->GetMethodID(clazz, "complete", "([B)[B") : nullptr;
    const auto ready = zenzai ? env->GetMethodID(clazz, "ready", "()V") : nullptr;
    env->DeleteLocalRef(clazz);
    if (!ready) {
        discard_callback_exception(env);
        return JNI_FALSE;
    }
    auto receiver = env->NewGlobalRef(callbacks);
    if (!receiver) return JNI_FALSE;
    // Environment and static Core paths may now be read by meltype_init.
    // Never retry an incomplete init with another data directory.
    init_attempted = true;
    if (setenv("MELTYPE_DATA_DIR", requested.c_str(), 1) != 0) {
        env->DeleteGlobalRef(receiver);
        return JNI_FALSE;
    }
    binding_storage = {vm, receiver, mozc, zenzai, ready};
    bindings.store(&binding_storage, std::memory_order_release);
    // Native initialization may already have started a worker even if initialization fails.
    if (meltype_android_init_with_zenzai(mozc_request, zenzai_request) != 1) return JNI_FALSE;
    meltype_set_conversion_ready(conversion_ready);
    data_dir = requested;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_ninjin0802_keyboard_app_core_NativeApi_create(
        JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(init_mutex);
    if (data_dir.empty()) return 0;
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(meltype_ime_create()));
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_ninjin0802_keyboard_app_core_NativeApi_destroy(
        JNIEnv*, jclass, jlong host) {
    if (host) meltype_ime_destroy(as_handle(host));
}

// One event (ImeEvent JSON) in, one response (ImeOutput JSON) out, both UTF-8.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_ninjin0802_keyboard_app_core_NativeApi_handle(
        JNIEnv* env, jclass, jlong host, jbyteArray event) {
    if (!host || !event) return nullptr;
    auto json = read_utf8(env, event);
    if (env->ExceptionCheck()) return nullptr;
    return return_utf8(env, meltype_ime_handle(as_handle(host), json.data()));
}
