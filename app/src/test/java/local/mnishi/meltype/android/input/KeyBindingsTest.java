// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

import android.view.KeyEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeyBindingsTest {
    @Test public void halfFullSwitchesModeAndControlHalfFullPauses() {
        assertEquals(KeyBindings.Action.TOGGLE_DIRECT,
                KeyBindings.match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, true));
        assertEquals(KeyBindings.Action.TOGGLE_PAUSE,
                KeyBindings.match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, KeyEvent.META_CTRL_RIGHT_ON, false));
    }

    @Test public void jisRoleKeysChooseTheirRequestedMode() {
        for (int code : new int[] {KeyEvent.KEYCODE_KANA, KeyEvent.KEYCODE_KATAKANA_HIRAGANA,
                KeyEvent.KEYCODE_HENKAN}) {
            assertEquals(KeyBindings.Action.JAPANESE, KeyBindings.match(code, 0, true));
        }
        for (int code : new int[] {KeyEvent.KEYCODE_EISU, KeyEvent.KEYCODE_MUHENKAN}) {
            assertEquals(KeyBindings.Action.DIRECT, KeyBindings.match(code, 0, true));
        }
    }

    @Test public void controlSpaceCanBeDisabledToPreserveApplicationBindings() {
        assertEquals(KeyBindings.Action.TOGGLE_DIRECT,
                KeyBindings.match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_LEFT_ON, true));
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON, false));
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_SPACE, 0, true));
    }

    @Test public void shiftSpaceRemainsAvailableForReverseCandidates() {
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON, true));
        assertNull(KeyBindings.match(KeyEvent.KEYCODE_SPACE,
                KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_RIGHT_ON, true));
    }

    @Test public void altMetaFunctionAndSymbolCombinationsNeverChangeImeMode() {
        for (int modifier : new int[] {KeyEvent.META_ALT_ON, KeyEvent.META_ALT_RIGHT_ON,
                KeyEvent.META_META_ON, KeyEvent.META_META_LEFT_ON,
                KeyEvent.META_FUNCTION_ON, KeyEvent.META_SYM_ON}) {
            for (int code : new int[] {KeyEvent.KEYCODE_ZENKAKU_HANKAKU, KeyEvent.KEYCODE_KANA,
                    KeyEvent.KEYCODE_KATAKANA_HIRAGANA, KeyEvent.KEYCODE_HENKAN,
                    KeyEvent.KEYCODE_MUHENKAN, KeyEvent.KEYCODE_EISU, KeyEvent.KEYCODE_SPACE}) {
                assertNull("key=" + code + ", modifier=" + modifier,
                        KeyBindings.match(code, modifier | KeyEvent.META_CTRL_ON, true));
                assertNull(KeyBindings.match(code, modifier, true));
            }
        }
    }

    @Test public void otherControlCombinationsStayWithTheApplication() {
        for (int code : new int[] {KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_KANA,
                KeyEvent.KEYCODE_KATAKANA_HIRAGANA, KeyEvent.KEYCODE_HENKAN,
                KeyEvent.KEYCODE_MUHENKAN, KeyEvent.KEYCODE_EISU}) {
            assertNull(KeyBindings.match(code, KeyEvent.META_CTRL_ON, true));
        }
    }

    @Test public void ordinaryEditingKeysAreNotModeBindings() {
        for (int code : new int[] {KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F10}) {
            assertNull(KeyBindings.match(code, 0, true));
        }
    }
}
