// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** Field-only policy. It never reads editor contents or identifies applications. */
public final class EditorPolicy {
    private EditorPolicy() {}

    public static FieldPolicy of(int inputType, int imeOptions) {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        boolean password = inputClass == InputType.TYPE_CLASS_TEXT && switch (variation) {
            case InputType.TYPE_TEXT_VARIATION_PASSWORD,
                 InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                 InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true;
            default -> false;
        };
        password |= inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean directText = inputClass == InputType.TYPE_CLASS_TEXT && switch (variation) {
            case InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                 InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> true;
            default -> false;
        };
        boolean bypass = inputClass != InputType.TYPE_CLASS_TEXT || password || directText;
        boolean privateInput = password || (imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        return new FieldPolicy(bypass, privateInput);
    }
}
