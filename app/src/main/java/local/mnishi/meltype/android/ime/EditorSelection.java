// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import java.util.ArrayDeque;
import java.util.function.IntPredicate;

/** Tracks UTF-16 selection positions from IME edits until the editor reports them. */
final class EditorSelection {
    private static final int MAX_PENDING = 64;
    private final ArrayDeque<Position> pending = new ArrayDeque<>();
    private Position expected = new Position(-1, -1, 0);

    void reset(int start, int end) {
        follow(Position.report(start, end, 0));
    }

    boolean hasSelection() { return expected.start >= 0 && expected.start != expected.end; }

    void replaced(int removed, int inserted, int composingLength) {
        if (expected.start < 0) return;
        int end = Math.max(0, expected.start - removed) + inserted;
        expect(end, composingLength);
    }

    void at(int offset) { expect(offset, 0); }

    void movedBack(int amount) {
        if (expected.start >= 0) expect(Math.max(0, expected.start - amount), 0);
    }

    void passedKey() { reset(-1, -1); }

    /** Returns true for a late or coalesced report from the IME's own edits. */
    boolean reported(int start, int end, int candidatesStart, int candidatesEnd, boolean composing,
                     IntPredicate stillAtOwnComposition) {
        int composingLength = candidatesStart >= 0 && candidatesEnd > candidatesStart
                ? candidatesEnd - candidatesStart : 0;
        Position report = Position.report(start, end, composingLength);
        if (report.start < 0) return true;

        // A caret inside a composing range indicates an external move, even if this caret
        // offset happened to be recorded for earlier, shorter text.
        boolean atComposingEnd = composingLength > 0 && start == end && end == candidatesEnd;
        if (composingLength > 0 && !atComposingEnd) {
            follow(report);
            return false;
        }

        Position match = null;
        for (Position edit : pending) {
            if (edit.matches(report)) {
                match = edit;
                break;
            }
        }
        if (match != null) {
            while (pending.removeFirst() != match) { }
            return true;
        }
        if (expected.start < 0 || expected.matches(report) || composing && atComposingEnd) {
            if (pending.isEmpty()) follow(report);
            return true;
        }
        // Some editors deliver a previous commit's no-span report even after acknowledging
        // the new composing text. Verify the live caret before throwing away that text state.
        if (composing && composingLength == 0 && start == end && expected.start == expected.end
                && stillAtOwnComposition != null && stillAtOwnComposition.test(expected.start)) return true;
        follow(report);
        return false;
    }

    private void expect(int end, int composingLength) {
        expected = new Position(end, end, composingLength);
        pending.addLast(expected);
        if (pending.size() > MAX_PENDING) pending.removeFirst();
    }

    private void follow(Position report) {
        pending.clear();
        expected = report;
    }

    private record Position(int start, int end, int composingLength) {
        static Position report(int start, int end, int composingLength) {
            return start < 0 || end < 0 ? new Position(-1, -1, 0)
                    : new Position(Math.min(start, end), Math.max(start, end), composingLength);
        }

        boolean matches(Position report) {
            // Some editors omit the composing range in their selection callbacks.
            return start == report.start && end == report.end
                    && (report.composingLength == 0 || composingLength == report.composingLength);
        }
    }
}
