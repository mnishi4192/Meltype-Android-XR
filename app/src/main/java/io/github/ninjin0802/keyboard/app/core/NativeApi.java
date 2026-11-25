// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.core;

import local.mnishi.meltype.android.core.OfficialNativeCallbacks;

/** NativeAOT ImeHost transport from Meltype 1.7.1. Text arguments are ordinary UTF-8. */
public final class NativeApi {
    static {
        System.loadLibrary("MeltypeNative");
        System.loadLibrary("meltype_official_jni");
    }

    private NativeApi() {}

    public static native boolean init(byte[] directoryUtf8, OfficialNativeCallbacks callbacks);
    public static native long create();
    public static native byte[] handle(long host, byte[] eventUtf8);
    public static native void destroy(long host);
}
