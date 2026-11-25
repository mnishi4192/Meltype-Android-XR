// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import local.mnishi.meltype.android.core.InputOptions;

/** Android-only input and CPU preferences. Zero threads means automatic selection. */
public final class MeltypePrefs {
    private static final String THREADS = "cpu_threads";
    private static final String CTRL_SPACE = "ctrl_space_toggles";
    private static final String HIDE_KEYBOARD_ON_PHYSICAL_TYPING = "hide_keyboard_on_physical_typing";
    private static final String SPACE_DRAG_MOVES_CURSOR = "space_drag_moves_cursor";
    private static final String BACKSPACE_DRAG_REPEATS_DELETION = "backspace_drag_repeats_deletion";
    private static final String LIVE_CONVERSION = "live_conversion";
    private static final String PUNCTUATION_AFTER_LATIN = "japanese_punctuation_after_latin";
    private static final String COMMIT_ON_PUNCTUATION = "commit_on_punctuation";
    private static final String PUNCTUATION_STYLE = "punctuation_style";
    private static final String PREFER_MIDDLE_DOT = "prefer_middle_dot";
    private static final String Z_SHORTCUTS = "z_shortcuts";
    private static final String FULL_WIDTH_SPACE = "prefer_full_width_space";
    private final SharedPreferences preferences;

    public MeltypePrefs(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("inference", Context.MODE_PRIVATE);
        // Earlier Android builds allowed GPU selection. Never retain that obsolete backend request.
        if (preferences.contains("inference_backend")) {
            preferences.edit().remove("inference_backend").apply();
        }
    }

    public int inferenceThreads() {
        try { return normalizeThreads(preferences.getInt(THREADS, 0)); }
        catch (ClassCastException ignored) { return 0; }
    }

    public void setInferenceThreads(int threads) {
        if (threads != normalizeThreads(threads)) throw new IllegalArgumentException("Unsupported thread count");
        preferences.edit().putInt(THREADS, threads).apply();
    }

    public static int normalizeThreads(int threads) {
        return switch (threads) {
            case 0, 1, 2, 4, 8 -> threads;
            default -> 0;
        };
    }

    public boolean ctrlSpaceToggles() {
        return readBoolean(CTRL_SPACE, true);
    }

    public void setCtrlSpaceToggles(boolean enabled) {
        writeBoolean(CTRL_SPACE, enabled);
    }

    public boolean hideKeyboardOnPhysicalTyping() {
        return readBoolean(HIDE_KEYBOARD_ON_PHYSICAL_TYPING, true);
    }

    void setHideKeyboardOnPhysicalTyping(boolean enabled) {
        writeBoolean(HIDE_KEYBOARD_ON_PHYSICAL_TYPING, enabled);
    }

    public boolean spaceDragMovesCursor() {
        return readBoolean(SPACE_DRAG_MOVES_CURSOR, true);
    }

    void setSpaceDragMovesCursor(boolean enabled) {
        writeBoolean(SPACE_DRAG_MOVES_CURSOR, enabled);
    }

    public boolean backspaceDragRepeatsDeletion() {
        return readBoolean(BACKSPACE_DRAG_REPEATS_DELETION, true);
    }

    void setBackspaceDragRepeatsDeletion(boolean enabled) {
        writeBoolean(BACKSPACE_DRAG_REPEATS_DELETION, enabled);
    }

    public InputOptions inputOptions() {
        InputOptions defaults = InputOptions.DEFAULT;
        return new InputOptions(
                readBoolean(LIVE_CONVERSION, defaults.liveConversion),
                readBoolean(PUNCTUATION_AFTER_LATIN, defaults.japanesePunctuationAfterLatin),
                readBoolean(COMMIT_ON_PUNCTUATION, defaults.commitOnPunctuation),
                punctuationStyle(defaults.punctuationStyle),
                readBoolean(PREFER_MIDDLE_DOT, defaults.preferMiddleDot),
                readBoolean(Z_SHORTCUTS, defaults.zShortcuts),
                readBoolean(FULL_WIDTH_SPACE, defaults.preferFullWidthSpace));
    }

    void setLiveConversion(boolean enabled) { writeBoolean(LIVE_CONVERSION, enabled); }

    void setJapanesePunctuationAfterLatin(boolean enabled) {
        writeBoolean(PUNCTUATION_AFTER_LATIN, enabled);
    }

    void setCommitOnPunctuation(boolean enabled) { writeBoolean(COMMIT_ON_PUNCTUATION, enabled); }

    void setPunctuationStyle(InputOptions.PunctuationStyle style) {
        preferences.edit().putString(PUNCTUATION_STYLE, style.name()).apply();
    }

    void setPreferMiddleDot(boolean enabled) { writeBoolean(PREFER_MIDDLE_DOT, enabled); }

    void setZShortcuts(boolean enabled) { writeBoolean(Z_SHORTCUTS, enabled); }

    void setPreferFullWidthSpace(boolean enabled) { writeBoolean(FULL_WIDTH_SPACE, enabled); }

    public static boolean isInferenceSetting(String key) {
        return key == null || THREADS.equals(key);
    }

    public static boolean isKeyboardSetting(String key) {
        return key == null || HIDE_KEYBOARD_ON_PHYSICAL_TYPING.equals(key)
                || SPACE_DRAG_MOVES_CURSOR.equals(key) || BACKSPACE_DRAG_REPEATS_DELETION.equals(key);
    }

    public static boolean isInputSetting(String key) {
        if (key == null) return true;
        return switch (key) {
            case LIVE_CONVERSION, PUNCTUATION_AFTER_LATIN, COMMIT_ON_PUNCTUATION, PUNCTUATION_STYLE,
                 PREFER_MIDDLE_DOT, Z_SHORTCUTS, FULL_WIDTH_SPACE -> true;
            default -> false;
        };
    }

    private InputOptions.PunctuationStyle punctuationStyle(InputOptions.PunctuationStyle fallback) {
        try { return InputOptions.PunctuationStyle.valueOf(preferences.getString(PUNCTUATION_STYLE, fallback.name())); }
        catch (ClassCastException | IllegalArgumentException ignored) { return fallback; }
    }

    private boolean readBoolean(String key, boolean fallback) {
        try { return preferences.getBoolean(key, fallback); }
        catch (ClassCastException ignored) { return fallback; }
    }

    private void writeBoolean(String key, boolean enabled) {
        preferences.edit().putBoolean(key, enabled).apply();
    }

    public void register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
