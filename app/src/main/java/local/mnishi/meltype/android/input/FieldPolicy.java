// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

/** Bypass keeps an editor's keys untouched; privateInput also records explicit privacy requests. */
public final class FieldPolicy {
    public final boolean bypass;
    public final boolean privateInput;

    public FieldPolicy(boolean bypass, boolean privateInput) {
        this.bypass = bypass;
        this.privateInput = privateInput;
    }
}
