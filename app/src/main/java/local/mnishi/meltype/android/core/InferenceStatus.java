// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Process-local status; never presents a saved previous session as currently running. */
public final class InferenceStatus {
    private static final Set<Consumer<String>> listeners = new CopyOnWriteArraySet<>();
    private static volatile String text = "入力方式を選択するとモデルを読み込みます。";

    private InferenceStatus() {}

    public static String get() { return text; }

    public static void set(String next) {
        text = next;
        for (Consumer<String> listener : listeners) listener.accept(next);
    }

    public static void observe(Consumer<String> listener) {
        listeners.add(listener);
        listener.accept(text);
    }

    public static void remove(Consumer<String> listener) { listeners.remove(listener); }
}
