// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.input;

/** Values captured from one Android key event, before consulting the conversion core. */
public final class KeyInput {
    public final int deviceId;
    public final int keyCode;
    public final int ch;
    public final int baseChar;
    public final int metaState;
    public final int repeatCount;
    public final long downTime;
    public final boolean physical;

    public KeyInput(int deviceId, int keyCode, int ch, int baseChar, int metaState,
                    int repeatCount, long downTime, boolean physical) {
        this.deviceId = deviceId;
        this.keyCode = keyCode;
        this.ch = ch;
        this.baseChar = baseChar;
        this.metaState = metaState;
        this.repeatCount = repeatCount;
        this.downTime = downTime;
        this.physical = physical;
    }
}
