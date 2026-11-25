// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EditorPolicyTest {
    @Test public void ordinarySingleAndMultilineTextCanCompose() {
        for (int inputType : new int[] {InputType.TYPE_CLASS_TEXT,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME}) {
            FieldPolicy policy = EditorPolicy.of(inputType, 0);
            assertFalse(policy.bypass);
            assertFalse(policy.privateInput);
        }
    }

    @Test public void everyPasswordVariationBypassesConversionAndIsPrivate() {
        int[] variations = {InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD};
        for (int variation : variations) {
            FieldPolicy policy = EditorPolicy.of(InputType.TYPE_CLASS_TEXT | variation, 0);
            assertTrue(policy.bypass);
            assertTrue(policy.privateInput);
        }
        FieldPolicy numberPassword = EditorPolicy.of(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0);
        assertTrue(numberPassword.bypass);
        assertTrue(numberPassword.privateInput);
    }

    @Test public void addressAndUriFieldsKeepTheirOrdinaryKeyboardInput() {
        for (int variation : new int[] {InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI}) {
            assertTrue(EditorPolicy.of(InputType.TYPE_CLASS_TEXT | variation, 0).bypass);
        }
    }

    @Test public void numberPhoneAndDateTimeFieldsBypassRegardlessOfOptions() {
        for (int inputType : new int[] {InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME,
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE,
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME}) {
            assertTrue(EditorPolicy.of(inputType, EditorInfo.IME_ACTION_DONE).bypass);
        }
    }

    @Test public void nullAndUnknownInputClassesDoNotStartComposition() {
        assertTrue(EditorPolicy.of(InputType.TYPE_NULL, 0).bypass);
        assertTrue(EditorPolicy.of(InputType.TYPE_TEXT_FLAG_MULTI_LINE, 0).bypass);
        assertTrue(EditorPolicy.of(0x0F, 0).bypass);
    }

    @Test public void noPersonalizedLearningMarksTextPrivateWithoutDisablingConversion() {
        FieldPolicy policy = EditorPolicy.of(InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_ACTION_SEARCH);
        assertFalse(policy.bypass);
        assertTrue(policy.privateInput);
    }

    @Test public void privacyFlagIsRetainedForBypassedFields() {
        FieldPolicy policy = EditorPolicy.of(InputType.TYPE_CLASS_PHONE,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        assertTrue(policy.bypass);
        assertTrue(policy.privateInput);
    }

    @Test public void unrelatedImeActionsAndFlagsDoNotMakeNormalTextPrivateOrBypassed() {
        FieldPolicy policy = EditorPolicy.of(InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION
                        | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        assertFalse(policy.bypass);
        assertFalse(policy.privateInput);
    }
}
