// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.view.inputmethod.EditorInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import local.mnishi.meltype.android.core.CompositionView;
import local.mnishi.meltype.android.core.OfficialCoreHost;
import local.mnishi.meltype.android.input.FieldPolicy;
import local.mnishi.meltype.android.input.KeyBindings;
import local.mnishi.meltype.android.input.KeyInput;
import local.mnishi.meltype.android.input.KeyNormalizer;
import local.mnishi.meltype.android.input.NormalizedKey;

/** Routes input to upstream ImeHost and retains Android's editor, device and IME-switch policies. */
final class OfficialImeController implements AutoCloseable {
    private final OfficialEditor editor;
    private final EditorSink sink;
    private final CandidateDisplay display;
    private final Consumer<Throwable> onFailure;
    private final Map<Long, Long> consumedDowns = new HashMap<>();
    private OfficialCoreHost core;
    private FieldPolicy policy = new FieldPolicy(true, true);
    private String app;
    private boolean active;
    private boolean paused;
    private boolean japanese = true;
    private boolean physical;
    private boolean ctrlSpace = true;
    private boolean commitOnPunctuation;
    private boolean reportedCaretMove;

    OfficialImeController(OfficialCoreHost core, OfficialEditor editor, EditorSink sink,
                          CandidateDisplay display, Consumer<Throwable> onFailure) {
        this.core = core;
        this.editor = editor;
        this.sink = sink;
        this.display = display;
        this.onFailure = onFailure;
    }

    void setSettings(boolean ctrlSpace, boolean commitOnPunctuation) {
        this.ctrlSpace = ctrlSpace;
        this.commitOnPunctuation = commitOnPunctuation;
    }

    String modeLabel() { return paused ? "⏸" : core != null && !policy.bypass && japanese ? "あ" : "A"; }
    boolean physicalPresentation() { return physical; }
    CompositionView view() { return editor.view(); }

    void startInput(FieldPolicy newPolicy, EditorInfo info) {
        if (editor.view() != null) {
            editor.finishInPlace();
            dispatch(() -> core.caret("CommittedByApp", null, false, true), false);
        }
        active = true;
        physical = false;
        reportedCaretMove = false;
        policy = newPolicy;
        app = info == null ? null : info.packageName;
        editor.resetSelection(info == null ? -1 : info.initialSelStart, info == null ? -1 : info.initialSelEnd);
        dispatch(() -> core.caret("CaretMoved", field(), true, false), false);
        updateDisplay();
    }

    void finishInput() {
        active = false;
        editor.finishInPlace();
        dispatch(() -> core.caret("CommittedByApp", null, false, true), false);
        physical = false;
        updateDisplay();
    }

    void selectionChanged(int start, int end, int candidatesStart, int candidatesEnd) {
        boolean own = editor.selectionUpdated(start, end, candidatesStart, candidatesEnd,
                active && !policy.bypass && !policy.privateInput);
        if (!active || policy.bypass || own || reportedCaretMove) return;
        finishCompositionInPlace();
        dispatch(() -> core.caret("CaretMoved", field(), false, false), false);
        reportedCaretMove = true;
    }

    private void finishCompositionInPlace() {
        if (editor.view() == null) return;
        editor.finishInPlace();
        dispatch(() -> core.caret("CommittedByApp", null, false, false), false);
    }

    boolean keyDown(KeyInput key) {
        if (!key.physical) return false;
        long identity = keyId(key.deviceId, key.keyCode);
        boolean previous = Long.valueOf(key.downTime).equals(consumedDowns.get(identity));
        boolean consumed = handleDown(key, previous);
        if (consumed) consumedDowns.put(identity, key.downTime);
        boolean handled = consumed || key.repeatCount > 0 && previous;
        if (!handled && active && !policy.bypass && !KeyNormalizer.isModifierOnly(key.keyCode)) editor.passedKey();
        return handled;
    }

    private boolean handleDown(KeyInput key, boolean previous) {
        if (!active || policy.bypass) return false;
        KeyBindings.Action action = KeyBindings.match(key.keyCode, key.metaState, ctrlSpace);
        if (action != null) {
            if (key.repeatCount > 0) return previous;
            physical = true;
            switch (action) {
                case TOGGLE_DIRECT -> toggleDirect();
                case JAPANESE -> changeMode("JapaneseModeSelected");
                case DIRECT -> changeMode("AlphanumericModeSelected");
                case TOGGLE_PAUSE -> {
                    commit();
                    paused = !paused;
                    updateDisplay();
                }
            }
            return true;
        }
        if (paused) return false;
        NormalizedKey normalized = KeyNormalizer.normalize(key);
        if (normalized == null) return false;
        physical = true;
        return key(normalized.vk, normalized.ch, normalized.modifiers, key.repeatCount > 0);
    }

    boolean keyUp(int deviceId, int keyCode, long downTime) {
        return consumedDowns.remove(keyId(deviceId, keyCode), downTime);
    }

    private static long keyId(int device, int key) { return (long) device << 32 | key & 0xffffffffL; }

    void selectCandidate(int index) {
        if (active) dispatch(() -> core.select(field(), index), true);
    }

    void softText(String text) {
        physical = false;
        if (!active || text == null || text.isEmpty()) return;
        if (policy.bypass || paused || core == null) {
            insertText(text);
            return;
        }
        for (int offset = 0; offset < text.length();) {
            int ch = text.codePointAt(offset);
            String glyph = new String(Character.toChars(ch));
            offset += Character.charCount(ch);
            if (!key(0, ch, Character.isUpperCase(ch) ? 1 : 0, false) && !insertText(glyph)) break;
        }
    }

    private boolean insertText(String text) {
        if (!sink.commit(text)) return false;
        editor.directTextInserted(text.length());
        return true;
    }

    void softSpace() { softText(" "); }

    boolean softMoveCursor(int keyCode) {
        if (!active) return false;
        physical = false;
        finishCompositionInPlace();
        reportedCaretMove = false;
        return sink.moveCursor(keyCode);
    }

    void softDelete() {
        if (!active) return;
        physical = false;
        if ((policy.bypass || paused || !key(0x08, 0, 0, false)) && sink.deleteBackward()) editor.passedKey();
    }

    void softEnter(int options, int actionId, boolean custom) {
        if (!active) return;
        physical = false;
        if (!policy.bypass && !paused && key(0x0D, 0, 0, false)) return;
        int action = custom ? actionId : options & EditorInfo.IME_MASK_ACTION;
        boolean useAction = (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && (custom || action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED);
        if (sink.enter(useAction ? action : 0, useAction)) editor.passedKey();
    }

    void softFlickText(String text) {
        if (!active || text == null || text.isEmpty()) return;
        physical = false;
        commit();
        insertText(text);
    }

    void convert(int vk) {
        physical = false;
        if (!policy.bypass && !paused) key(vk, vk == 0x20 ? ' ' : 0, 0, false);
    }

    void toggleDirect() { changeMode("ModeToggleRequested"); }

    private void changeMode(String event) {
        if (!policy.bypass && !paused) dispatch(() -> core.event(event, field()), true);
    }

    void prepareImeSwitch() { commit(); }

    void conversionFinished() {
        if (active && !paused && !policy.bypass && editor.view() != null) {
            dispatch(() -> core.event("ConversionFinished", field()), true);
        }
    }

    private boolean key(int vk, int ch, int modifiers, boolean repeat) {
        boolean consumed = dispatch(() -> core.key(field(), vk, ch, modifiers, repeat), true);
        if (consumed && commitOnPunctuation && modifiers == 0 && isPunctuationKey(ch)
                && shouldConfirmPunctuation()) commit();
        return consumed;
    }

    private static boolean isPunctuationKey(int ch) { return ch > 0 && ",.、。，．".indexOf(ch) >= 0; }

    // Preserve the Android punctuation-confirm preference as a request to the same official Core.
    private boolean shouldConfirmPunctuation() {
        CompositionView view = editor.view();
        if (view == null || view.text.isEmpty()) return false;
        int last = view.text.length() - 1;
        if ("、。，．".indexOf(view.text.charAt(last)) < 0) return false;
        if (last == 0) return true;
        char before = view.text.charAt(last - 1);
        return before >= 128 || !Character.isLetterOrDigit(before);
    }

    private void commit() {
        if (editor.view() != null) dispatch(() -> core.caret("CommitRequested", field(), false, false), true);
    }

    private OfficialCoreHost.Field field() { return editor.field(policy, app); }

    private boolean dispatch(Supplier<OfficialCoreHost.Output> event, boolean applyEdits) {
        if (core == null) return false;
        try {
            if (applyEdits) reportedCaretMove = false;
            OfficialCoreHost.Output output = event.get();
            japanese = output.japanese();
            if (applyEdits && !editor.apply(output, physical)) {
                editor.finishInPlace();
                core.caret("CommittedByApp", null, false, false);
                updateDisplay();
                // The editor may have applied a partial result; don't send the same key again.
                return output.consumed();
            }
            updateDisplay();
            return output.consumed();
        } catch (RuntimeException | LinkageError failure) {
            close();
            onFailure.accept(failure);
            updateDisplay();
            return false;
        }
    }

    private void updateDisplay() {
        display.showComposition(editor.view());
        display.showMode(modeLabel());
    }

    @Override public void close() {
        editor.finishInPlace();
        OfficialCoreHost previous = core;
        core = null;
        if (previous != null) previous.close();
    }
}
