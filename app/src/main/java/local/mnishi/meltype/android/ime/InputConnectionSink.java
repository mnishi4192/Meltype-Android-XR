// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.function.Supplier;

public final class InputConnectionSink implements EditorSink {
    private final Supplier<InputConnection> connection;
    private final Supplier<EditorInfo> editor;

    public InputConnectionSink(Supplier<InputConnection> connection, Supplier<EditorInfo> editor) {
        this.connection = connection;
        this.editor = editor;
    }

    @Override public boolean commit(String text) {
        InputConnection current = connection.get();
        return current != null && current.commitText(text, 1);
    }
    @Override public boolean deleteBackward() {
        InputConnection current = connection.get();
        if (current == null) return false;
        EditorInfo info = editor.get();
        if (info == null || info.inputType == InputType.TYPE_NULL) {
            return sendKey(current, KeyEvent.KEYCODE_DEL);
        }
        CharSequence selection = current.getSelectedText(0);
        if (selection != null && selection.length() > 0) return current.commitText("", 1);
        return current.deleteSurroundingTextInCodePoints(1, 0) || sendKey(current, KeyEvent.KEYCODE_DEL);
    }

    @Override public boolean moveCursor(int keyCode) {
        InputConnection current = connection.get();
        return current != null && sendKey(current, keyCode);
    }

    @Override public boolean enter(int action, boolean useAction) {
        InputConnection current = connection.get();
        if (current == null) return false;
        if (useAction && current.performEditorAction(action)) return true;
        EditorInfo info = editor.get();
        if (info != null && (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && (info.inputType & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0) {
            return current.commitText("\n", 1);
        }
        return sendKey(current, KeyEvent.KEYCODE_ENTER);
    }

    private static boolean sendKey(InputConnection connection, int keyCode) {
        long time = SystemClock.uptimeMillis();
        int flags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        boolean pressed = connection.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
        boolean released = connection.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
        return pressed && released;
    }
}
