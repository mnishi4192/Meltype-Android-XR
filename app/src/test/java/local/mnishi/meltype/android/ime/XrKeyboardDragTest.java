// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import static local.mnishi.meltype.android.ime.XrKeyboardView.DragDirection.*;
import static local.mnishi.meltype.android.ime.XrKeyboardView.DragMode.CURSOR;
import static local.mnishi.meltype.android.ime.XrKeyboardView.DragMode.DELETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XrKeyboardDragTest {
    @Test public void movementWithinTheThresholdDoesNotTurnTapsIntoDrags() {
        assertFalse(pastThreshold(-13, 6, 14));
        assertEquals(NONE, at(CURSOR, -13, 6, 14));
        assertEquals(NONE, at(DELETE, -13, 6, 14));
        assertTrue(pastThreshold(-14, 6, 14));
    }

    @Test public void cursorDragFollowsTheDominantAxisInAllFourDirections() {
        assertEquals(LEFT, at(CURSOR, -28, 8, 14));
        assertEquals(RIGHT, at(CURSOR, 25, -10, 14));
        assertEquals(UP, at(CURSOR, 15, -26, 14));
        assertEquals(DOWN, at(CURSOR, -15, 30, 14));
    }

    @Test public void deleteDragRequiresALeftwardMotionRatherThanAnySwipe() {
        assertEquals(LEFT, at(DELETE, -30, 8, 14));
        assertEquals(NONE, at(DELETE, 30, 8, 14));
        assertEquals(NONE, at(DELETE, -6, -30, 14));
        assertEquals(NONE, at(DELETE, -18, 17, 14));
        assertTrue(pastThreshold(30, 8, 14)); // An unsupported swipe still suppresses the tap.
    }
}
