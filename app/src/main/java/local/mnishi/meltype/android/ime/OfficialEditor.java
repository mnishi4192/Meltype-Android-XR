// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import local.mnishi.meltype.android.core.CompositionView;
import local.mnishi.meltype.android.core.OfficialCoreHost;
import local.mnishi.meltype.android.input.FieldPolicy;

/** Maps the official output to Android's composing span; validates a correction at the editor boundary. */
final class OfficialEditor {
    private final Supplier<InputConnection> connection;
    private final EditorSelection selection = new EditorSelection();
    private CompositionView shown;
    private boolean lastPhysical;

    OfficialEditor(Supplier<InputConnection> connection) { this.connection = connection; }

    CompositionView view() { return shown; }

    void resetSelection(int start, int end) { selection.reset(start, end); }

    boolean selectionUpdated(int start, int end, int candidatesStart, int candidatesEnd, boolean readable) {
        return selection.reported(start, end, candidatesStart, candidatesEnd, shown != null,
                readable ? this::caretStillAtComposition : null);
    }

    void directTextInserted(int utf16Length) { selection.replaced(0, utf16Length, 0); }

    void passedKey() { selection.passedKey(); }

    OfficialCoreHost.Field field(FieldPolicy policy, String packageName) {
        String shownText = shown == null ? "" : shown.text;
        boolean composing = !shownText.isEmpty();
        List<String> traits = policy.bypass
                ? policy.privateInput ? Collections.singletonList("Password") : Collections.singletonList("NonText")
                : policy.privateInput ? Collections.singletonList("Private") : Collections.emptyList();
        InputConnection input = policy.bypass || policy.privateInput ? null : connection.get();
        String before = null;
        String after = null;
        String selected = null;
        if (input != null) {
            try {
                before = text(input.getTextBeforeCursor(4000 + shownText.length(), 0));
                if (composing && before != null) before = before.endsWith(shownText)
                        ? before.substring(0, before.length() - shownText.length()) : null;
                after = text(input.getTextAfterCursor(20, 0));
                if (!composing && selection.hasSelection()) {
                    selected = text(input.getSelectedText(0));
                }
            } catch (RuntimeException unreadable) {
                before = null;
                after = null;
                selected = null;
            }
        }
        return new OfficialCoreHost.Field(before, after, selected, composing, before != null, packageName, traits);
    }

    boolean apply(OfficialCoreHost.Output output, boolean physical) {
        InputConnection input = connection.get();
        if (input == null) return output.edits().isEmpty() && output.view() == null;
        if (output.edits().isEmpty() && output.view() == null && shown == null) return true;
        input.beginBatchEdit();
        try {
            for (OfficialCoreHost.Edit edit : output.edits()) {
                if (!applyEdit(input, edit)) return false;
            }
            CompositionView next = output.view();
            if (next == null && shown != null) {
                if (!input.setComposingText("", 1) || !input.finishComposingText()) return false;
                selection.replaced(shown.text.length(), 0, 0);
            } else if (next != null && changed(next, physical)) {
                if (!input.setComposingText(styled(next, physical), 1)) return false;
                selection.replaced(shownLength(), next.text.length(), next.text.length());
            }
            shown = next;
            lastPhysical = physical;
            return true;
        } finally {
            input.endBatchEdit();
        }
    }

    private boolean applyEdit(InputConnection input, OfficialCoreHost.Edit edit) {
        int composingLength = shownLength();
        String remove = edit.removeBefore();
        if (remove != null && !remove.isEmpty()) {
            String expected = remove + (shown == null ? "" : shown.text);
            if (!expected.equals(text(input.getTextBeforeCursor(expected.length(), 0)))) return false;
            if (shown != null) {
                if (!input.setComposingText("", 1)) return false;
                selection.replaced(composingLength, 0, 0);
                composingLength = 0;
            }
            if (!input.deleteSurroundingText(remove.length(), 0)) return false;
            selection.replaced(remove.length(), 0, 0);
        }
        if (!input.commitText(edit.text(), 1)) return false;
        selection.replaced(composingLength, edit.text().length(), 0);
        shown = null;
        if (edit.moveCaretBack() > 0) moveCaretBack(input, edit.moveCaretBack());
        return true;
    }

    private int shownLength() { return shown == null ? 0 : shown.text.length(); }

    private boolean caretStillAtComposition(int expected) {
        InputConnection input = connection.get();
        if (input == null || shown == null) return false;
        String composing = shown.text;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                SurroundingText text = input.getSurroundingText(composing.length(), 0, 0);
                if (text != null && text.getOffset() >= 0) {
                    return endsAtCaret(text.getText(), text.getSelectionStart(), text.getSelectionEnd(),
                            text.getOffset(), expected, composing);
                }
            }
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = composing.length();
            request.hintMaxLines = 1;
            ExtractedText text = input.getExtractedText(request, 0);
            return text != null && endsAtCaret(text.text, text.selectionStart, text.selectionEnd,
                    text.startOffset, expected, composing);
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean endsAtCaret(CharSequence text, int start, int end, int offset, int expected,
                                       String composing) {
        return text != null && start == end && start >= composing.length() && start <= text.length()
                && offset >= 0 && (long) offset + start == expected
                && TextUtils.regionMatches(text, start - composing.length(), composing, 0, composing.length());
    }

    private boolean changed(CompositionView next, boolean physical) {
        return shown == null || !shown.text.equals(next.text) || lastPhysical != physical
                || physical && (shown.clauseStart != next.clauseStart || shown.clauseEnd != next.clauseEnd);
    }

    private static CharSequence styled(CompositionView view, boolean physical) {
        if (!physical || view.clauseStart < 0 || view.clauseEnd <= view.clauseStart) return view.text;
        SpannableString text = new SpannableString(view.text);
        text.setSpan(new BackgroundColorSpan(0x4D36A7B4), view.clauseStart, view.clauseEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    void finishInPlace() {
        InputConnection input = connection.get();
        if (input != null && shown != null) input.finishComposingText();
        shown = null;
    }

    private void moveCaretBack(InputConnection input, int amount) {
        ExtractedText text = input.getExtractedText(new ExtractedTextRequest(), 0);
        if (text != null && text.selectionStart >= 0) {
            int offset = Math.max(0, text.startOffset + text.selectionStart - amount);
            if (input.setSelection(offset, offset)) selection.at(offset);
        } else {
            for (int i = 0; i < amount; i++) {
                input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));
            }
            selection.movedBack(amount);
        }
    }

    private static String text(CharSequence text) { return text == null ? null : text.toString(); }
}
