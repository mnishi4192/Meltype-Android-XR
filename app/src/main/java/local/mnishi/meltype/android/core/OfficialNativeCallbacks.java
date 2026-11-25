// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package local.mnishi.meltype.android.core;

import android.content.Context;
import android.util.Log;
import io.github.ninjin0802.keyboard.mozc.MozcBackend;
import io.github.ninjin0802.keyboard.mozc.MozcClause;
import io.github.ninjin0802.keyboard.mozc.MozcConverter;
import io.github.ninjin0802.keyboard.mozc.MozcEngine;
import io.github.ninjin0802.keyboard.mozc.MozcSegment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/** Process-wide engine callbacks for the official Core scheduler, adapted from CoreMozcCallbacks.kt. */
public final class OfficialNativeCallbacks {
    private static final String TAG = "MeltypeCore";
    private static final String UNIT = "\u001f";
    private static final String RECORD = "\u001e";
    private static final byte[] EMPTY = new byte[0];
    private static OfficialNativeCallbacks instance;

    private final Context application;
    private final AtomicReference<Runnable> readyListener = new AtomicReference<>();
    private final Object mozcLock = new Object();
    private final Object cpuLock = new Object();
    private volatile MozcBackend mozc;
    private boolean mozcOpened;
    private NativeCore cpu;
    private volatile int cpuThreads = -1;
    private volatile int configuredThreads;

    private OfficialNativeCallbacks(Context application) { this.application = application; }

    public static synchronized OfficialNativeCallbacks get(Context context) {
        if (instance == null) instance = new OfficialNativeCallbacks(context.getApplicationContext());
        return instance;
    }

    public void setReadyListener(Runnable listener) { readyListener.set(listener); }

    public void clearReadyListener(Runnable listener) { readyListener.compareAndSet(listener, null); }

    public void setThreadCount(int threads) {
        if (threads < 0 || threads > 8) throw new IllegalArgumentException("CPU thread count must be 0..8");
        if (configuredThreads == threads) return;
        configuredThreads = threads;
        InferenceStatus.set("次のZenzai推論でCPUスレッド設定を反映します。");
    }

    public boolean isMozcReady() { return mozc != null; }

    public boolean isCpuReady() { return cpuThreads == configuredThreads; }

    /** Called by the C# converter. The requested protocol is the official Mozc helper's UTF-8 line. */
    @SuppressWarnings("unused") // Called from official_bridge.cpp.
    public byte[] request(byte[] requestBytes) {
        String[] fields = new String(requestBytes, StandardCharsets.UTF_8).split("\t", 3);
        if ("S".equals(fields[0])) return utf8("OK"); // Learned changes save immediately in Mozc.
        try {
            // Reverse conversion can run on the input thread. Avoid waiting for dictionary extraction.
            boolean reverse = "R".equals(fields[0]);
            MozcBackend backend = reverse ? mozc : openMozc();
            if (backend == null) return reverse ? EMPTY : null;
            return switch (fields[0]) {
                case "C" -> fields.length < 3 ? null : convert(backend, fields[2], optional(fields[1]));
                case "L" -> fields.length < 3 ? null : learn(backend, fields[2], optional(fields[1]));
                case "R" -> fields.length < 2 ? EMPTY : reading(backend, fields[1]);
                default -> null;
            };
        } catch (RuntimeException | LinkageError failure) {
            Log.w(TAG, "Mozc callback: " + failure.getClass().getSimpleName());
            return null;
        }
    }

    /** The official C# Core generates and validates the Zenzai prompt and response. */
    @SuppressWarnings("unused") // Called from official_bridge.cpp.
    public byte[] complete(byte[] promptBytes) {
        String prompt = new String(promptBytes, StandardCharsets.UTF_8);
        synchronized (cpuLock) {
            try {
                int threads = configuredThreads;
                if (cpuThreads != threads) {
                    closeCpu();
                    InferenceStatus.set("CPUのモデルを読み込み中…");
                    cpu = new NativeCore(ModelStore.prepare(application).getAbsolutePath(), threads);
                    cpuThreads = threads;
                    InferenceStatus.set(cpu.backend() + "でZenzai変換できます。");
                }
                return utf8(cpu.generate(prompt, 96));
            } catch (IOException | RuntimeException | LinkageError failure) {
                closeCpu();
                InferenceStatus.set("CPUでのZenzai推論に失敗しました。Mozcで変換できます。");
                Log.w(TAG, "CPU callback: " + failure.getClass().getSimpleName());
                return null;
            }
        }
    }

    @SuppressWarnings("unused") // Called on the C# conversion worker by official_bridge.cpp.
    public void ready() {
        Runnable listener = readyListener.get();
        if (listener != null) listener.run();
    }

    private MozcBackend openMozc() {
        synchronized (mozcLock) {
            if (mozcOpened) return mozc;
            mozcOpened = true;
            MozcEngine engine = MozcEngine.Companion.open(application);
            if (engine != null) mozc = new MozcConverter(engine);
            return mozc;
        }
    }

    private void closeCpu() {
        cpuThreads = -1;
        if (cpu != null) cpu.close();
        cpu = null;
    }

    private static byte[] convert(MozcBackend backend, String reading, String context) {
        List<MozcSegment> found = backend.convert(reading, context);
        if (found == null) return null;
        StringBuilder convertedReading = new StringBuilder();
        StringJoiner records = new StringJoiner(RECORD);
        for (MozcSegment segment : found) {
            convertedReading.append(segment.getReading());
            StringJoiner fields = new StringJoiner(UNIT).add(segment.getReading());
            for (String candidate : segment.getCandidates()) fields.add(candidate);
            records.add(fields.toString());
        }
        return reading.contentEquals(convertedReading) ? utf8(records.toString()) : EMPTY;
    }

    private static byte[] learn(MozcBackend backend, String records, String context) {
        List<MozcClause> clauses = new ArrayList<>();
        for (String record : records.split(RECORD, -1)) {
            int split = record.indexOf(UNIT);
            if (split <= 0 || split == record.length() - 1) return utf8("NG");
            clauses.add(new MozcClause(record.substring(0, split), record.substring(split + 1)));
        }
        return utf8(backend.learn(clauses, context) ? "OK" : "NG");
    }

    private static byte[] reading(MozcBackend backend, String text) {
        String reading = backend.reading(text);
        return reading == null ? EMPTY : utf8(reading);
    }

    private static byte[] utf8(String response) {
        if (response == null || response.indexOf('\0') >= 0) return null;
        return response.getBytes(StandardCharsets.UTF_8);
    }

    private static String optional(String value) { return value.isEmpty() ? null : value; }
}
