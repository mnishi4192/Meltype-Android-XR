// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import local.mnishi.meltype.android.core.CompositionView;

public interface CandidateDisplay {
    void showComposition(CompositionView view);
    void showMode(String label);
}
