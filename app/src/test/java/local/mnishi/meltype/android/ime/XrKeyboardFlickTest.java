// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import static local.mnishi.meltype.android.ime.XrKeyboardView.FlickDirection.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XrKeyboardFlickTest {
    @Test public void ignoresOrdinaryTapJitter() {
        assertEquals(TAP, at(9, -12, 14));
    }

    @Test public void identifiesBothVerticalDirections() {
        assertEquals(UP, at(4, -14, 14));
        assertEquals(DOWN, at(-8, 16, 14));
    }

    @Test public void cancelsSidewaysAndAmbiguousDiagonalDrags() {
        assertEquals(CANCEL, at(30, 2, 14));
        assertEquals(CANCEL, at(18, -20, 14));
    }
}
