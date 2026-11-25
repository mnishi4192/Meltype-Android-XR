// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KeyNormalizerTest {
    private KeyInput key(int keyCode, int ch, int baseChar, int metaState) {
        return new KeyInput(4, keyCode, ch, baseChar, metaState, 0, 1234L, true);
    }

    private NormalizedKey normalized(int keyCode, int ch, int baseChar, int metaState) {
        NormalizedKey result = KeyNormalizer.normalize(key(keyCode, ch, baseChar, metaState));
        assertNotNull(result);
        return result;
    }

    @Test public void usesLetterVirtualKeysWithoutLosingTypedCase() {
        NormalizedKey lower = normalized(KeyEvent.KEYCODE_A, 'a', 'a', 0);
        assertEquals(0x41, lower.vk);
        assertEquals('a', lower.ch);
        assertEquals(0, lower.modifiers);
        NormalizedKey upper = normalized(KeyEvent.KEYCODE_A, 'A', 'a', KeyEvent.META_SHIFT_RIGHT_ON);
        assertEquals(0x41, upper.vk);
        assertEquals('A', upper.ch);
        assertEquals(Modifiers.SHIFT, upper.modifiers);
    }

    @Test public void physicalTypingIncludesShiftedCharactersAndSpace() {
        assertTrue(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_A, 'A', 'a', KeyEvent.META_SHIFT_ON)));
        assertTrue(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_COMMA, ',', ',', 0)));
        assertTrue(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_SPACE, ' ', ' ', 0)));
    }

    @Test public void shortcutsNavigationAndVirtualKeysAreNotPhysicalTyping() {
        assertFalse(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_A, 1, 'a', KeyEvent.META_CTRL_ON)));
        assertFalse(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_A, 'a', 'a', KeyEvent.META_ALT_ON)));
        assertFalse(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_SPACE, ' ', ' ', KeyEvent.META_META_ON)));
        assertFalse(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_DPAD_LEFT, 0, 0, 0)));
        assertFalse(KeyNormalizer.isPhysicalTyping(key(KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0, 0)));
        assertFalse(KeyNormalizer.isPhysicalTyping(new KeyInput(-1, KeyEvent.KEYCODE_A, 'a', 'a', 0, 0, 1L, false)));
    }

    @Test public void ctrlShortcutsKeepTheirBaseLetterWhenAndroidReturnsAControlCharacter() {
        NormalizedKey zero = normalized(KeyEvent.KEYCODE_A, 0, 'a', KeyEvent.META_CTRL_LEFT_ON);
        assertEquals(0x41, zero.vk);
        assertEquals('a', zero.ch);
        assertEquals(Modifiers.CONTROL, zero.modifiers);
        NormalizedKey controlA = normalized(KeyEvent.KEYCODE_A, 1, 'a', KeyEvent.META_CTRL_ON);
        assertEquals(zero.vk, controlA.vk);
        assertEquals(zero.ch, controlA.ch);
        assertEquals(zero.modifiers, controlA.modifiers);
    }

    @Test public void altCharacterLayersRemainAltShortcuts() {
        NormalizedKey changed = normalized(KeyEvent.KEYCODE_Q, '@', 'q', KeyEvent.META_ALT_RIGHT_ON);
        assertEquals('@', changed.ch);
        assertEquals(Modifiers.ALT, changed.modifiers);
        NormalizedKey unicode = normalized(KeyEvent.KEYCODE_O, 'ø', 'o', KeyEvent.META_ALT_ON);
        assertEquals('ø', unicode.ch);
        assertEquals(Modifiers.ALT, unicode.modifiers);
    }

    @Test public void combinesLeftAndRightModifierBitsWithoutRequiringAggregateBits() {
        NormalizedKey result = normalized(KeyEvent.KEYCODE_A, 'A', 'a',
                KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON
                        | KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_META_RIGHT_ON);
        assertEquals(Modifiers.SHIFT | Modifiers.CONTROL | Modifiers.ALT | Modifiers.META,
                result.modifiers);
    }

    @Test public void mapsEditingAndConversionKeysToTheReferenceCoreProtocol() {
        int[][] keys = {
                {KeyEvent.KEYCODE_ENTER, 0x0D}, {KeyEvent.KEYCODE_NUMPAD_ENTER, 0x0D},
                {KeyEvent.KEYCODE_DPAD_CENTER, 0x0D}, {KeyEvent.KEYCODE_TAB, 0x09},
                {KeyEvent.KEYCODE_DEL, 0x08}, {KeyEvent.KEYCODE_FORWARD_DEL, 0x2E},
                {KeyEvent.KEYCODE_ESCAPE, 0x1B}, {KeyEvent.KEYCODE_DPAD_LEFT, 0x25},
                {KeyEvent.KEYCODE_DPAD_UP, 0x26}, {KeyEvent.KEYCODE_DPAD_RIGHT, 0x27},
                {KeyEvent.KEYCODE_DPAD_DOWN, 0x28}, {KeyEvent.KEYCODE_MOVE_HOME, 0x24},
                {KeyEvent.KEYCODE_MOVE_END, 0x23}, {KeyEvent.KEYCODE_PAGE_UP, 0x21},
                {KeyEvent.KEYCODE_PAGE_DOWN, 0x22}, {KeyEvent.KEYCODE_F6, 0x75},
                {KeyEvent.KEYCODE_F7, 0x76}, {KeyEvent.KEYCODE_F8, 0x77},
                {KeyEvent.KEYCODE_F9, 0x78}, {KeyEvent.KEYCODE_F10, 0x79}
        };
        for (int[] pair : keys) {
            NormalizedKey result = normalized(pair[0], 0, 0, 0);
            assertEquals("Android key=" + pair[0], pair[1], result.vk);
            assertEquals(0, result.ch);
        }
    }

    @Test public void keepsSpaceAndItsShiftForReverseCandidateNavigation() {
        NormalizedKey result = normalized(KeyEvent.KEYCODE_SPACE, ' ', ' ', KeyEvent.META_SHIFT_ON);
        assertEquals(0x20, result.vk);
        assertEquals(' ', result.ch);
        assertEquals(Modifiers.SHIFT, result.modifiers);
    }

    @Test public void preservesModifiersOnEditorNavigation() {
        NormalizedKey result = normalized(KeyEvent.KEYCODE_DPAD_LEFT, 0, 0,
                KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        assertEquals(0x25, result.vk);
        assertEquals(Modifiers.CONTROL | Modifiers.SHIFT, result.modifiers);
    }

    @Test public void usesTheReferencePunctuationVirtualKeysAndKeepsShiftedSymbols() {
        int[][] punctuation = {{',', 0xBC}, {'.', 0xBE}, {'-', 0xBD}, {'/', 0xBF}, {'[', 0xDB}, {']', 0xDD}};
        for (int[] pair : punctuation) {
            NormalizedKey result = normalized(KeyEvent.KEYCODE_UNKNOWN, pair[0], pair[0], 0);
            assertEquals(pair[1], result.vk);
            assertEquals(pair[0], result.ch);
        }
        NormalizedKey exclamation = normalized(KeyEvent.KEYCODE_1, '!', '1', KeyEvent.META_SHIFT_ON);
        assertEquals(0x07, exclamation.vk);
        assertEquals('!', exclamation.ch);
        assertEquals(Modifiers.SHIFT, exclamation.modifiers);
    }

    @Test public void acceptsUnicodeScalarValuesWithoutTruncatingSupplementaryCharacters() {
        assertEquals('あ', normalized(KeyEvent.KEYCODE_UNKNOWN, 'あ', 0, 0).ch);
        NormalizedKey supplementary = normalized(KeyEvent.KEYCODE_UNKNOWN, 0x1F642, 0, 0);
        assertEquals(0x1F642, supplementary.ch);
        assertEquals(0x07, supplementary.vk);
    }

    @Test public void leavesDeadKeysAndInvalidOrControlCharactersWithTheEditor() {
        assertNull(KeyNormalizer.normalize(key(KeyEvent.KEYCODE_E,
                KeyCharacterMap.COMBINING_ACCENT | '\u0301', 'e', 0)));
        for (int character : new int[] {0, 7, 0x1F, 0x7F, 0x80, 0x9F, 0xD800, 0xDFFF, 0x110000, -1}) {
            assertNull("character=" + character,
                    KeyNormalizer.normalize(key(KeyEvent.KEYCODE_UNKNOWN, character, 0, 0)));
        }
    }

    @Test public void modifierKeysNeverBecomeCharactersEvenWhenADeviceSuppliesUnicode() {
        for (int code : new int[] {KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
                KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_SCROLL_LOCK,
                KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_FUNCTION}) {
            assertNull("Android key=" + code, KeyNormalizer.normalize(key(code, 'a', 'a', 0)));
        }
    }

    @Test public void unrepresentedFunctionAndSymbolModifiersStayWithTheEditor() {
        assertNull(KeyNormalizer.normalize(key(KeyEvent.KEYCODE_A, 'a', 'a', KeyEvent.META_FUNCTION_ON)));
        assertNull(KeyNormalizer.normalize(key(KeyEvent.KEYCODE_A, 'a', 'a', KeyEvent.META_SYM_ON)));
    }

    @Test public void physicalDeviceFilteringIsLeftToTheController() {
        NormalizedKey result = KeyNormalizer.normalize(new KeyInput(-1, KeyEvent.KEYCODE_A,
                'a', 'a', 0, 0, 1L, false));
        assertNotNull(result);
        assertEquals('a', result.ch);
    }
}
