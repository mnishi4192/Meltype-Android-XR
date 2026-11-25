// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/** Adapts Android keys to the reference core's virtual-key convention without changing shortcuts. */
public final class KeyNormalizer {
    public static final int VK_F6 = 0x75;
    public static final int VK_F7 = 0x76;
    public static final int VK_F10 = 0x79;
    private static final int OTHER_CHARACTER_KEY = 0x07;

    private KeyNormalizer() {}

    /** Character entry from a hardware keyboard, including Shift and Space. */
    public static boolean isPhysicalTyping(KeyInput key) {
        if (key == null || !key.physical) return false;
        NormalizedKey normalized = normalize(key);
        return normalized != null && normalized.ch != 0
                && (normalized.modifiers & (Modifiers.CONTROL | Modifiers.ALT | Modifiers.META)) == 0;
    }

    public static NormalizedKey normalize(KeyInput key) {
        if (key == null || isModifierOnly(key.keyCode)) return null;
        // The core has no Sym/Fn modifier bits. Keep those combinations with the application.
        if ((key.metaState & (KeyEvent.META_SYM_ON | KeyEvent.META_FUNCTION_ON)) != 0) return null;
        int modifiers = modifiersOf(key.metaState);
        int special = specialKey(key.keyCode);
        if (special != 0) return new NormalizedKey(special, special == 0x20 ? 0x20 : 0, modifiers);

        int character = key.ch;
        if (character == 0 || ((modifiers & Modifiers.CONTROL) != 0
                && character < 0x20 && character >= 0 && printable(key.baseChar))) {
            character = key.baseChar;
        }
        if (!printable(character)) return null;
        int vk;
        if (character >= 'a' && character <= 'z') vk = character - 0x20;
        else if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) vk = character;
        else vk = switch (character) {
            case ',' -> 0xBC;
            case '.' -> 0xBE;
            case '-' -> 0xBD;
            case '/' -> 0xBF;
            case '[' -> 0xDB;
            case ']' -> 0xDD;
            default -> OTHER_CHARACTER_KEY;
        };
        return new NormalizedKey(vk, character, modifiers);
    }

    public static boolean isModifierOnly(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
                 KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
                 KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
                 KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
                 KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_CAPS_LOCK,
                 KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_SCROLL_LOCK -> true;
            default -> false;
        };
    }

    private static boolean printable(int character) {
        return character != 0 && (character & KeyCharacterMap.COMBINING_ACCENT) == 0
                && Character.isValidCodePoint(character) && !Character.isISOControl(character)
                && !(character >= Character.MIN_SURROGATE && character <= Character.MAX_SURROGATE);
    }

    private static int modifiersOf(int metaState) {
        int modifiers = 0;
        if ((metaState & KeyEvent.META_SHIFT_MASK) != 0) modifiers |= Modifiers.SHIFT;
        if ((metaState & KeyEvent.META_CTRL_MASK) != 0) modifiers |= Modifiers.CONTROL;
        // Alt remains a modifier even if the device reports a different Unicode character.
        if ((metaState & KeyEvent.META_ALT_MASK) != 0) modifiers |= Modifiers.ALT;
        if ((metaState & KeyEvent.META_META_MASK) != 0) modifiers |= Modifiers.META;
        return modifiers;
    }

    private static int specialKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> 0x0D;
            case KeyEvent.KEYCODE_TAB -> 0x09;
            case KeyEvent.KEYCODE_SPACE -> 0x20;
            case KeyEvent.KEYCODE_DEL -> 0x08;
            case KeyEvent.KEYCODE_FORWARD_DEL -> 0x2E;
            case KeyEvent.KEYCODE_ESCAPE -> 0x1B;
            case KeyEvent.KEYCODE_DPAD_LEFT -> 0x25;
            case KeyEvent.KEYCODE_DPAD_UP -> 0x26;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27;
            case KeyEvent.KEYCODE_DPAD_DOWN -> 0x28;
            case KeyEvent.KEYCODE_MOVE_HOME -> 0x24;
            case KeyEvent.KEYCODE_MOVE_END -> 0x23;
            case KeyEvent.KEYCODE_PAGE_UP -> 0x21;
            case KeyEvent.KEYCODE_PAGE_DOWN -> 0x22;
            case KeyEvent.KEYCODE_F6 -> VK_F6;
            case KeyEvent.KEYCODE_F7 -> VK_F7;
            case KeyEvent.KEYCODE_F8 -> 0x77;
            case KeyEvent.KEYCODE_F9 -> 0x78;
            case KeyEvent.KEYCODE_F10 -> VK_F10;
            default -> 0;
        };
    }
}
