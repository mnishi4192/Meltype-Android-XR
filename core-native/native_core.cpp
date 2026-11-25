// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <android/log.h>
#include "llama.h"

#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr uint32_t CONTEXT_TOKENS = 1024;
constexpr int MAX_OUTPUT_TOKENS = 128;

struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    // An empty explicit device list prevents any offload-device selection.
    ggml_backend_dev_t devices[1] = {nullptr};
    ~Engine() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};

std::once_flag initialized;

void log_message(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN && level != GGML_LOG_LEVEL_CONT) {
        __android_log_write(level == GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_ERROR,
                            "MeltypeCore", text);
    }
}

void throw_java(JNIEnv * env, const std::exception & error) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, error.what());
}

// JNI's GetStringUTFChars/NewStringUTF use modified UTF-8; models require real UTF-8.
std::string from_java(JNIEnv * env, jstring value) {
    if (!value) throw std::runtime_error("Text must not be null");
    auto release = [env, value](const jchar * chars) { env->ReleaseStringChars(value, chars); };
    std::unique_ptr<const jchar, decltype(release)> pinned(env->GetStringChars(value, nullptr), release);
    if (!pinned) throw std::runtime_error("Unable to read text");
    const jchar * chars = pinned.get();
    jsize length = env->GetStringLength(value);
    std::string result;
    result.reserve(length * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        if (cp < 0x80) result += static_cast<char>(cp);
        else if (cp < 0x800) {
            result += static_cast<char>(0xC0 | (cp >> 6));
            result += static_cast<char>(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            result += static_cast<char>(0xE0 | (cp >> 12));
            result += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            result += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            result += static_cast<char>(0xF0 | (cp >> 18));
            result += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
            result += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            result += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }
    return result;
}

jstring to_java(JNIEnv * env, const std::string & value) {
    std::vector<jchar> chars;
    for (size_t i = 0; i < value.size();) {
        const auto first = static_cast<unsigned char>(value[i++]);
        uint32_t cp;
        int trailing;
        if (first < 0x80) { cp = first; trailing = 0; }
        else if (first >= 0xC2 && first <= 0xDF) { cp = first & 0x1F; trailing = 1; }
        else if (first >= 0xE0 && first <= 0xEF) { cp = first & 0x0F; trailing = 2; }
        else if (first >= 0xF0 && first <= 0xF4) { cp = first & 0x07; trailing = 3; }
        else throw std::runtime_error("Inference returned invalid UTF-8");
        if (i + trailing > value.size()) throw std::runtime_error("Inference returned incomplete UTF-8");
        for (int n = 0; n < trailing; ++n) {
            auto next = static_cast<unsigned char>(value[i++]);
            if ((next & 0xC0) != 0x80) throw std::runtime_error("Inference returned invalid UTF-8");
            cp = (cp << 6) | (next & 0x3F);
        }
        if ((trailing == 1 && cp < 0x80) || (trailing == 2 && cp < 0x800)
                || (trailing == 3 && cp < 0x10000) || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            throw std::runtime_error("Inference returned invalid UTF-8");
        }
        if (cp < 0x10000) chars.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            chars.push_back(static_cast<jchar>(0xD800 | (cp >> 10)));
            chars.push_back(static_cast<jchar>(0xDC00 | (cp & 0x3FF)));
        }
    }
    static const jchar empty = 0;
    return env->NewString(chars.empty() ? &empty : chars.data(), static_cast<jsize>(chars.size()));
}

Engine * require_engine(jlong handle) {
    if (!handle) throw std::runtime_error("Inference engine is closed");
    return reinterpret_cast<Engine *>(handle);
}

void set_batch(llama_batch_ext * batch, const llama_token * tokens, int count, int position) {
    llama_batch_ext_clear(batch);
    for (int i = 0; i < count; ++i) {
        int index = llama_batch_ext_add_token(batch, 0, tokens[i]);
        if (index < 0) throw std::runtime_error("Unable to construct inference input batch");
        llama_pos pos = position + i;
        if (!llama_batch_ext_set_pos(batch, index, &pos)) throw std::runtime_error("Unable to set inference token position");
    }
    llama_batch_ext_set_output_logits(batch, count - 1, true);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_local_mnishi_meltype_android_core_NativeCore_nativeCreate(
        JNIEnv * env, jclass, jstring model_path, jint threads) {
    try {
        std::call_once(initialized, [] { llama_log_set(log_message, nullptr); llama_backend_init(); });
        auto engine = std::make_unique<Engine>();
        auto params = llama_model_default_params();
        params.devices = engine->devices;
        params.n_gpu_layers = 0;
        params.split_mode = LLAMA_SPLIT_MODE_NONE;
        const auto path = from_java(env, model_path);
        engine->model = llama_model_load_from_file(path.c_str(), params);
        if (!engine->model) throw std::runtime_error("Unable to load inference model on CPU.");
        auto context_params = llama_context_default_params();
        context_params.n_ctx = CONTEXT_TOKENS;
        context_params.n_batch = CONTEXT_TOKENS;
        context_params.n_ubatch = 128;
        context_params.n_threads = std::clamp(static_cast<int>(threads), 1, 8);
        context_params.n_threads_batch = context_params.n_threads;
        context_params.offload_kqv = false;
        context_params.op_offload = false;
        context_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        engine->context = llama_init_from_model(engine->model, context_params);
        if (!engine->context) throw std::runtime_error("Unable to create CPU inference context.");
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception & error) { throw_java(env, error); return 0; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_local_mnishi_meltype_android_core_NativeCore_nativeGenerate(
        JNIEnv * env, jclass, jlong handle, jstring prompt_text, jint max_tokens) {
    try {
        auto engine = require_engine(handle);
        if (max_tokens < 1 || max_tokens > MAX_OUTPUT_TOKENS) throw std::runtime_error("Output token limit must be 1..128");
        const auto prompt = from_java(env, prompt_text);
        if (prompt.empty() || prompt.size() > 4096) return to_java(env, "");
        const auto vocab = llama_model_get_vocab(engine->model);
        // Zenzai's GGUF specifies add_bos_token=false. Its PUA prompt markers are literal text.
        int needed = -llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), nullptr, 0, false, true);
        if (needed <= 0) throw std::runtime_error("Unable to tokenize reading");
        if (static_cast<uint32_t>(needed + max_tokens) > llama_n_ctx(engine->context)) return to_java(env, "");
        std::vector<llama_token> tokens(needed);
        int count = llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), tokens.data(), needed, false, true);
        if (count != needed) throw std::runtime_error("Unable to tokenize reading");
        llama_memory_clear(llama_get_memory(engine->context), true);
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
        std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(llama_batch_ext_init(engine->context), llama_batch_ext_free);
        if (!sampler || !batch) throw std::runtime_error("Unable to allocate inference request");
        set_batch(batch.get(), tokens.data(), count, 0);
        std::string output;
        for (int generated = 0; generated < max_tokens; ++generated) {
            if (llama_process(engine->context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
                throw std::runtime_error("Inference failed while evaluating model");
            }
            llama_token token = llama_sampler_sample(sampler.get(), engine->context, -1);
            if (llama_vocab_is_eog(vocab, token)) return to_java(env, output);
            llama_sampler_accept(sampler.get(), token);
            std::vector<char> piece(256);
            int length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
            if (length < 0) {
                piece.resize(-length);
                length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
            }
            if (length < 0) throw std::runtime_error("Unable to decode inference result");
            output.append(piece.data(), length);
            set_batch(batch.get(), &token, 1, count + generated);
        }
        // A request that does not finish is not a broken CPU backend. Discard
        // its partial output and leave the engine available for the next reading.
        return to_java(env, "");
    } catch (const std::exception & error) { throw_java(env, error); return nullptr; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_local_mnishi_meltype_android_core_NativeCore_nativeBackend(JNIEnv * env, jclass, jlong handle) {
    try { require_engine(handle); return to_java(env, "CPU"); }
    catch (const std::exception & error) { throw_java(env, error); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_local_mnishi_meltype_android_core_NativeCore_nativeDestroy(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}
