// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EditorSelectionTest {
    @Test public void delayedCommitDoesNotDismissTheNextComposingCharacter() {
        EditorSelection selection = new EditorSelection();
        selection.reset(0, 0);
        selection.replaced(0, 1, 1); // 橋, still selected in conversion.
        assertTrue(selection.reported(1, 1, 0, 1, true, null));

        selection.replaced(1, 1, 0); // One key commits 橋 and ...
        selection.replaced(0, 1, 1); // ... begins composing k.
        assertTrue(selection.reported(1, 1, -1, -1, true, null)); // Late report for 橋 alone.

        selection.replaced(1, 1, 1); // k becomes か at the same UTF-16 position.
        assertTrue(selection.reported(2, 2, 1, 2, true, null));
        assertFalse(selection.reported(0, 0, -1, -1, true, null));
    }

    @Test public void coalescedReportDiscardsEarlierOffsets() {
        EditorSelection selection = new EditorSelection();
        selection.reset(0, 0);
        selection.replaced(0, 1, 1);
        selection.replaced(1, 2, 2);
        selection.replaced(2, 3, 3);

        assertTrue(selection.reported(3, 3, 0, 3, true, null));
        assertFalse(selection.reported(1, 1, -1, -1, false, null));
    }

    @Test public void actualCaretDistinguishesAStaleReportFromARealCaretMove() {
        EditorSelection selection = new EditorSelection();
        selection.reset(0, 0);
        selection.replaced(0, 1, 1); // 橋
        selection.replaced(1, 1, 0); // Commit 橋.
        selection.replaced(0, 1, 1); // k
        assertTrue(selection.reported(2, 2, 1, 2, true, null));

        // An app may report the old commit after acknowledging the new composition.
        assertTrue(selection.reported(1, 1, -1, -1, true, expected -> expected == 2));

        // When the actual caret really is at 1, the same report is an external move.
        assertFalse(selection.reported(1, 1, -1, -1, true, expected -> expected == 1));
    }

    @Test public void skipsReadbackWhenEditorReportsAnActualSelection() {
        EditorSelection selection = new EditorSelection();
        selection.reset(0, 0);
        selection.replaced(0, 2, 2);
        assertTrue(selection.reported(2, 2, 0, 2, true, null));

        assertFalse(selection.reported(0, 1, -1, -1, true, expected -> {
            throw new AssertionError("An external selection must not be ignored");
        }));
        assertTrue(selection.hasSelection());
    }

    @Test public void earlierCaretOffsetInsideLongerCompositionIsAnExternalMove() {
        EditorSelection selection = new EditorSelection();
        selection.reset(0, 0);
        selection.replaced(0, 1, 1);
        selection.replaced(1, 2, 2);

        assertFalse(selection.reported(1, 1, 0, 2, true, null));
    }

    @Test public void reversedSelectionIsReplacedInUtf16Units() {
        EditorSelection selection = new EditorSelection();
        selection.reset(8, 4);
        assertTrue(selection.hasSelection());

        selection.replaced(0, "🙂".length(), 0);
        assertTrue(selection.reported(6, 6, -1, -1, false, null));
        assertFalse(selection.hasSelection());
    }

    @Test public void firstReportAfterPassedKeyReestablishesThePosition() {
        EditorSelection selection = new EditorSelection();
        selection.reset(3, 3);
        selection.passedKey();
        assertTrue(selection.reported(0, 8, -1, -1, false, null));
        assertTrue(selection.hasSelection());
        selection.replaced(0, 1, 1);
        assertTrue(selection.reported(1, 1, 0, 1, true, null));
    }
}
