// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

import android.view.KeyEvent;

/** IME mode bindings applied before ordinary key normalization. */
public final class KeyBindings {
    public enum Action { TOGGLE_DIRECT, JAPANESE, DIRECT, TOGGLE_PAUSE }

    private KeyBindings() {}

    public static Action match(int keyCode, int metaState, boolean ctrlSpaceToggles) {
        if ((metaState & (KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK
                | KeyEvent.META_SYM_ON | KeyEvent.META_FUNCTION_ON)) != 0) return null;
        boolean control = (metaState & KeyEvent.META_CTRL_MASK) != 0;
        boolean shift = (metaState & KeyEvent.META_SHIFT_MASK) != 0;
        if (keyCode == KeyEvent.KEYCODE_ZENKAKU_HANKAKU) {
            return control ? Action.TOGGLE_PAUSE : Action.TOGGLE_DIRECT;
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return control && !shift && ctrlSpaceToggles ? Action.TOGGLE_DIRECT : null;
        }
        if (control) return null;
        return switch (keyCode) {
            case KeyEvent.KEYCODE_KANA, KeyEvent.KEYCODE_KATAKANA_HIRAGANA,
                 KeyEvent.KEYCODE_HENKAN -> Action.JAPANESE;
            case KeyEvent.KEYCODE_EISU, KeyEvent.KEYCODE_MUHENKAN -> Action.DIRECT;
            default -> null;
        };
    }
}
