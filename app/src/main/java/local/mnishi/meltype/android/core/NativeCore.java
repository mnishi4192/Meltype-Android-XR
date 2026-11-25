// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

/** One CPU-only llama.cpp session; all methods run on the inference worker. */
public final class NativeCore implements AutoCloseable {
    static { System.loadLibrary("meltype_core"); }

    private long handle;

    public NativeCore(String modelPath, int configuredThreads) {
        if (configuredThreads < 0 || configuredThreads > 8) {
            throw new IllegalArgumentException("CPU thread count must be 0..8");
        }
        int threads = configuredThreads == 0
                ? Math.max(1, Math.min(4, java.lang.Runtime.getRuntime().availableProcessors()))
                : configuredThreads;
        handle = nativeCreate(modelPath, threads);
        if (handle == 0) throw new IllegalStateException("Unable to create CPU inference session");
    }

    public String generate(String prompt, int maxTokens) {
        if (handle == 0) throw new IllegalStateException("Inference session is closed");
        return nativeGenerate(handle, prompt, maxTokens);
    }

    public String backend() {
        if (handle == 0) throw new IllegalStateException("Inference session is closed");
        return nativeBackend(handle);
    }

    @Override public void close() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private static native long nativeCreate(String modelPath, int threads);
    private static native String nativeGenerate(long handle, String prompt, int maxTokens);
    private static native String nativeBackend(long handle);
    private static native void nativeDestroy(long handle);
}
