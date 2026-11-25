// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

/** Snapshot of the conversion and symbol preferences for an input session. */
public final class InputOptions {
    public enum PunctuationStyle {
        MARU_TEN('、', '。'),
        PERIOD_COMMA('，', '．'),
        MARU_COMMA('，', '。'),
        PERIOD_TEN('、', '．');

        public final char comma;
        public final char period;

        PunctuationStyle(char comma, char period) {
            this.comma = comma;
            this.period = period;
        }
    }

    public static final InputOptions DEFAULT = new InputOptions(
            true, true, false, PunctuationStyle.MARU_TEN, true, true, false);

    public final boolean liveConversion;
    public final boolean japanesePunctuationAfterLatin;
    public final boolean commitOnPunctuation;
    public final PunctuationStyle punctuationStyle;
    public final boolean preferMiddleDot;
    public final boolean zShortcuts;
    public final boolean preferFullWidthSpace;

    public InputOptions(boolean liveConversion, boolean japanesePunctuationAfterLatin,
                        boolean commitOnPunctuation, PunctuationStyle punctuationStyle,
                        boolean preferMiddleDot, boolean zShortcuts, boolean preferFullWidthSpace) {
        this.liveConversion = liveConversion;
        this.japanesePunctuationAfterLatin = japanesePunctuationAfterLatin;
        this.commitOnPunctuation = commitOnPunctuation;
        this.punctuationStyle = punctuationStyle;
        this.preferMiddleDot = preferMiddleDot;
        this.zShortcuts = zShortcuts;
        this.preferFullWidthSpace = preferFullWidthSpace;
    }
}
