// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable composition snapshot; the Android adapter owns how it is displayed. */
public final class CompositionView {
    public final String text;
    public final String raw;
    public final List<String> candidates;
    public final int selectedIndex;
    public final boolean converting;
    public final boolean numbered;
    public final int clauseStart;
    public final int clauseEnd;

    public CompositionView(String text, String raw, List<String> candidates, int selectedIndex,
                           boolean converting, boolean numbered, int clauseStart, int clauseEnd) {
        this.text = text;
        this.raw = raw;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.selectedIndex = selectedIndex;
        this.converting = converting;
        this.numbered = numbered;
        this.clauseStart = clauseStart;
        this.clauseEnd = clauseEnd;
    }
}
