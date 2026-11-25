// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

/** Core-facing key: Windows virtual-key number, Unicode code point, and modifier mask. */
public final class NormalizedKey {
    public final int vk;
    public final int ch;
    public final int modifiers;

    public NormalizedKey(int vk, int ch, int modifiers) {
        this.vk = vk;
        this.ch = ch;
        this.modifiers = modifiers;
    }
}
