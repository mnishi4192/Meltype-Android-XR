// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.CursorAnchorInfo;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import local.mnishi.meltype.android.R;
import local.mnishi.meltype.android.core.CompositionView;

/** A non-focusable, on-demand candidate list positioned at the editor's composing text. */
final class HardwareCandidatePopup {
    private static final int PAGE_SIZE = 9;
    private static final int SURFACE = 0xff192c3d;
    private static final int BORDER = 0xff4e6e80;
    private static final int FOREGROUND = 0xffecf6fb;
    private static final int NUMBER = 0xffa6c8d7;
    private static final int SELECTED = 0xff34596b;
    private static final int HOVER = 0xff28475a;

    private final View imeAnchor;
    private final IntConsumer choose;
    private final Runnable visibilityChanged;
    private final ScrollView content;
    private final LinearLayout rows;
    private final PopupWindow popup;
    private final Runnable refresh = this::update;
    private final int rowHeight;
    private final int edgePadding;
    private final int[] screenLocation = new int[2];
    private final Rect visibleFrame = new Rect();
    private CursorAnchorInfo cursor;
    private CompositionView composition;
    private int clauseOffset;
    private List<String> displayedCandidates = Collections.emptyList();
    private int displayedPage = -1;
    private boolean displayedNumbered;

    HardwareCandidatePopup(View imeAnchor, IntConsumer choose, Runnable visibilityChanged) {
        this.imeAnchor = imeAnchor;
        this.choose = choose;
        this.visibilityChanged = visibilityChanged;
        Context context = imeAnchor.getContext();
        rowHeight = dp(42);
        edgePadding = dp(5);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(edgePadding, edgePadding, edgePadding, edgePadding);
        rows.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
        content = new ScrollView(context);
        content.setFocusable(false);
        content.setContentDescription(context.getText(R.string.hardware_candidates));
        content.addView(rows, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        popup = new PopupWindow(content, 0, 0, false);
        GradientDrawable background = shape(SURFACE, 12);
        background.setStroke(dp(1), BORDER);
        popup.setBackgroundDrawable(background);
        popup.setElevation(dp(9));
        popup.setIsClippedToScreen(true);
        popup.setIsLaidOutInScreen(true);
        popup.setAttachedInDecor(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnDismissListener(visibilityChanged::run);

        imeAnchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { scheduleUpdate(); }
            @Override public void onViewDetachedFromWindow(View view) { reset(); }
        });
        imeAnchor.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                oldBottom) -> {
            if (composition != null && (left != oldLeft || top != oldTop
                    || right != oldRight || bottom != oldBottom)) scheduleUpdate();
        });
    }

    void show(CompositionView view, int activeClauseStart) {
        composition = view != null && view.selectedIndex >= 0
                && view.selectedIndex < view.candidates.size() ? view : null;
        clauseOffset = activeClauseStart;
        if (composition == null) {
            if (view == null) cursor = null;
            imeAnchor.removeCallbacks(refresh);
            dismiss();
        } else {
            scheduleUpdate();
        }
    }

    void onCursorAnchorInfo(CursorAnchorInfo info) {
        cursor = info;
        if (composition != null) scheduleUpdate();
    }

    boolean isShowing() { return popup.isShowing(); }

    void reset() {
        show(null, -1);
    }

    private void scheduleUpdate() {
        imeAnchor.removeCallbacks(refresh);
        imeAnchor.post(refresh);
    }

    private void update() {
        if (composition == null || cursor == null || !imeAnchor.isShown() || !imeAnchor.isAttachedToWindow()) {
            dismiss();
            return;
        }
        RectF caret = editorAnchor();
        if (caret == null) {
            dismiss();
            return;
        }
        imeAnchor.getWindowVisibleDisplayFrame(visibleFrame);
        imeAnchor.getLocationOnScreen(screenLocation);
        int edge = dp(6);
        visibleFrame.inset(edge, edge);
        visibleFrame.bottom = Math.min(visibleFrame.bottom, screenLocation[1] - edge);
        if (visibleFrame.isEmpty() || caret.right < visibleFrame.left - edge
                || caret.left > visibleFrame.right + edge
                || caret.bottom < visibleFrame.top - edge || caret.top > visibleFrame.bottom + edge) {
            dismiss();
            return;
        }
        int page = composition.selectedIndex / PAGE_SIZE;
        int first = page * PAGE_SIZE;
        int count = Math.min(PAGE_SIZE, composition.candidates.size() - first);
        int width = Math.min(dp(244), visibleFrame.width());
        int panelHeight = count * rowHeight + edgePadding * 2;
        int below = (int) Math.ceil(caret.bottom) + edge;
        int above = (int) Math.floor(caret.top) - edge;
        int belowRoom = visibleFrame.bottom - below;
        int aboveRoom = above - visibleFrame.top;
        boolean placeBelow = belowRoom >= panelHeight || belowRoom >= aboveRoom;
        int height = Math.min(panelHeight, placeBelow ? belowRoom : aboveRoom);
        if (height < rowHeight + edgePadding * 2) {
            dismiss();
            return;
        }
        int x = Math.max(visibleFrame.left, Math.min(Math.round(caret.left), visibleFrame.right - width));
        int y = placeBelow ? below : above - height;

        updateRows(first, count, page, composition.selectedIndex);
        try {
            if (popup.isShowing()) {
                popup.update(x, y, width, height);
            } else {
                popup.setWidth(width);
                popup.setHeight(height);
                popup.showAtLocation(imeAnchor, Gravity.TOP | Gravity.LEFT, x, y);
                visibilityChanged.run();
            }
            content.post(() -> showSelectedRow(composition == null ? -1 : composition.selectedIndex % PAGE_SIZE));
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException ignored) {
            // Some editors disappear between the anchor callback and window attachment.
            dismiss();
        }
    }

    private RectF editorAnchor() {
        int composingStart = cursor.getComposingTextStart();
        if (clauseOffset >= 0 && composingStart >= 0 && cursor.getComposingText() != null
                && clauseOffset < cursor.getComposingText().length()) {
            int index = composingStart + clauseOffset;
            RectF bounds = cursor.getCharacterBounds(index);
            if (bounds != null && canSee(cursor.getCharacterBoundsFlags(index))) {
                return toScreen(bounds);
            }
        }
        if (!canSee(cursor.getInsertionMarkerFlags())) return null;
        float x = cursor.getInsertionMarkerHorizontal();
        return toScreen(new RectF(x, cursor.getInsertionMarkerTop(), x, cursor.getInsertionMarkerBottom()));
    }

    private RectF toScreen(RectF rect) {
        cursor.getMatrix().mapRect(rect);
        return Float.isFinite(rect.left) && Float.isFinite(rect.top)
                && Float.isFinite(rect.right) && Float.isFinite(rect.bottom)
                && rect.bottom >= rect.top ? rect : null;
    }

    private static boolean canSee(int flags) {
        return (flags & CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION) == 0
                || (flags & CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION) != 0;
    }

    private void updateRows(int first, int count, int page, int selected) {
        if (displayedPage != page || displayedNumbered != composition.numbered
                || !displayedCandidates.equals(composition.candidates)) {
            displayedPage = page;
            displayedNumbered = composition.numbered;
            displayedCandidates = composition.candidates;
            rows.removeAllViews();
            for (int i = 0; i < count; i++) rows.addView(row(first + i, i + 1),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));
            content.scrollTo(0, 0);
        }
        for (int i = 0; i < count; i++) rows.getChildAt(i).setSelected(first + i == selected);
    }

    private View row(int index, int number) {
        Context context = imeAnchor.getContext();
        String candidate = displayedCandidates.get(index);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(9), 0, dp(8), 0);
        row.setFocusable(false);
        row.setContentDescription(displayedNumbered
                ? context.getString(R.string.hardware_candidate, number, candidate) : candidate);
        row.setOnClickListener(ignored -> choose.accept(index));

        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_selected}, shape(SELECTED, 8));
        background.addState(new int[]{android.R.attr.state_hovered}, shape(HOVER, 8));
        background.addState(new int[]{}, shape(SURFACE, 8));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44a5e6ef), background, null));

        if (displayedNumbered) {
            TextView key = new TextView(context);
            key.setText(String.valueOf(number));
            key.setTextColor(NUMBER);
            key.setTextSize(13);
            key.setTypeface(Typeface.MONOSPACE);
            key.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(key, new LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.MATCH_PARENT));
        }

        TextView value = new TextView(context);
        value.setText(candidate);
        value.setTextColor(FOREGROUND);
        value.setTextSize(18);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return row;
    }

    private void showSelectedRow(int index) {
        if (!popup.isShowing() || index < 0 || index >= rows.getChildCount()) return;
        View row = rows.getChildAt(index);
        int scroll = content.getScrollY();
        if (row.getTop() < scroll) content.scrollTo(0, row.getTop());
        else if (row.getBottom() > scroll + content.getHeight()) {
            content.scrollTo(0, row.getBottom() - content.getHeight());
        }
    }

    private void dismiss() {
        if (popup.isShowing()) popup.dismiss();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int size) {
        return Math.round(size * imeAnchor.getResources().getDisplayMetrics().density);
    }
}
