// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

/** Direct editor operations used for keys the official Core passes through. */
public interface EditorSink {
    boolean commit(String text);
    /** Deletes the editor selection, or one preceding Unicode code point if nothing is selected. */
    boolean deleteBackward();
    /** Lets the editor move the caret using its own visual lines and bidirectional layout. */
    boolean moveCursor(int keyCode);
    /** Performs an editor action when requested, with newline/legacy-key fallback in the Android adapter. */
    boolean enter(int action, boolean useAction);
}
