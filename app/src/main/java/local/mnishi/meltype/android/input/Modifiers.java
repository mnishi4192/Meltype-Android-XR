// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

/** Modifier bits used by the Meltype core; these are independent of Android's bit positions. */
public final class Modifiers {
    public static final int SHIFT = 1;
    public static final int CONTROL = 2;
    public static final int ALT = 4;
    public static final int META = 8;

    private Modifiers() {}
}
