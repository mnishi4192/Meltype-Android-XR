// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Candidate controls only; the editor retains physical-keyboard focus. */
@SuppressLint("ViewConstructor") // Created programmatically with the service's required listener.
final class CandidateStripView extends LinearLayout {
    interface Listener {
        void onCandidateSelected(int index);
    }

    private final Listener listener;
    private final Consumer<Boolean> onExpanded;
    private final HorizontalScrollView stripScroll;
    private final LinearLayout strip;
    private final TextView expandButton;
    private final ScrollView listScroll;
    private final LinearLayout list;
    private final int foreground;
    private final int selectionBackground;
    private final int selectionForeground;
    private final int targetSize;
    private final int targetGap;
    private final boolean roomy;
    private final boolean dark;
    private List<String> candidates = Collections.emptyList();
    private int selectedIndex = -1;
    private boolean expanded;

    CandidateStripView(Context context, Listener listener, Consumer<Boolean> onExpanded) {
        super(context);
        this.listener = listener;
        this.onExpanded = onExpanded;
        setOrientation(VERTICAL);
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setPadding(dp(4), dp(4), dp(4), dp(4));

        Configuration config = getResources().getConfiguration();
        roomy = PanelButton.roomy(config);
        dark = roomy || (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        targetSize = dp(roomy ? 60 : 48);
        targetGap = dp(roomy ? 8 : 4);
        foreground = dark ? Color.rgb(229, 231, 235) : Color.rgb(32, 33, 36);
        selectionBackground = dark ? Color.rgb(66, 85, 112) : Color.rgb(211, 227, 253);
        selectionForeground = dark ? Color.rgb(225, 237, 255) : Color.rgb(4, 30, 73);
        setBackgroundColor(dark ? Color.rgb(32, 33, 36) : Color.rgb(245, 247, 250));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setFocusable(false);
        addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        stripScroll = new HorizontalScrollView(context);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setFillViewport(false);
        stripScroll.setFocusable(false);
        top.addView(stripScroll, new LayoutParams(0, targetSize, 1));
        strip = new LinearLayout(context);
        strip.setOrientation(HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        stripScroll.addView(strip, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        expandButton = new TextView(context);
        expandButton.setGravity(Gravity.CENTER);
        expandButton.setTextSize(24);
        expandButton.setTextColor(foreground);
        expandButton.setFocusable(false);
        expandButton.setBackground(PanelButton.background(expandButton, idleColor(), 0xff62d9ca,
                pressedColor(), hoverColor()));
        expandButton.setOnClickListener(view -> setExpanded(!expanded));
        top.addView(expandButton, new LayoutParams(targetSize, targetSize));

        listScroll = new ScrollView(context);
        listScroll.setFocusable(false);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(true);
        int visibleRows = config.screenHeightDp > 0 && config.screenHeightDp < 480 ? 1 : 3;
        addView(listScroll, new LayoutParams(LayoutParams.MATCH_PARENT, targetSize * visibleRows));
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        listScroll.addView(list, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        clear();
    }

    void setCandidates(List<String> values, int selectedIndex) {
        int nextSelection = selectedIndex >= 0 && selectedIndex < values.size()
                ? selectedIndex : -1;
        if (candidates.equals(values) && strip.getChildCount() == values.size()) {
            int previous = this.selectedIndex;
            if (previous != nextSelection) {
                this.selectedIndex = nextSelection;
                refreshSelection(previous);
                refreshSelection(nextSelection);
                post(this::showSelection);
            }
            setVisibility(values.isEmpty() ? View.GONE : View.VISIBLE);
            updateExpansion();
            return;
        }
        candidates = Collections.unmodifiableList(new ArrayList<>(values));
        this.selectedIndex = nextSelection;
        strip.removeAllViews();
        list.removeAllViews();
        for (int index = 0; index < candidates.size(); index++) {
            TextView chip = candidate(index, false);
            LayoutParams chipParams = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, targetSize);
            chipParams.setMarginEnd(targetGap);
            strip.addView(chip, chipParams);
            list.addView(candidate(index, true), new LayoutParams(
                    LayoutParams.MATCH_PARENT, targetSize));
        }
        if (candidates.size() < 2) setExpanded(false);
        expandButton.setVisibility(candidates.size() > 1 ? View.VISIBLE : View.GONE);
        setVisibility(candidates.isEmpty() ? View.GONE : View.VISIBLE);
        updateExpansion();
        post(this::showSelection);
    }

    void setExpanded(boolean expanded) {
        boolean next = expanded && candidates.size() > 1;
        if (this.expanded == next) return;
        this.expanded = next;
        updateExpansion();
        post(this::showSelection);
        onExpanded.accept(next);
    }

    boolean isExpanded() {
        return expanded;
    }

    void clear() {
        setCandidates(Collections.emptyList(), -1);
    }

    private TextView candidate(int index, boolean fullWidth) {
        TextView view = new TextView(getContext());
        String text = candidates.get(index);
        view.setText(text);
        view.setTextSize(roomy ? 22 : 18);
        view.setGravity((fullWidth ? Gravity.START : Gravity.CENTER_HORIZONTAL)
                | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setMinWidth(dp(roomy ? 88 : 64));
        if (!fullWidth) view.setMaxWidth(dp(roomy ? 340 : 240));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setFocusable(false);
        view.setOnClickListener(ignored -> listener.onCandidateSelected(index));
        styleCandidate(view, index);
        return view;
    }

    private void refreshSelection(int index) {
        if (index < 0) return;
        styleCandidate((TextView) strip.getChildAt(index), index);
        styleCandidate((TextView) list.getChildAt(index), index);
    }

    private void styleCandidate(TextView view, int index) {
        boolean selected = index == selectedIndex;
        view.setTextColor(selected ? selectionForeground : foreground);
        view.setSelected(selected);
        view.setContentDescription(candidates.get(index) + "、候補" + (index + 1)
                + (selected ? "、選択中" : ""));
        view.setBackground(PanelButton.background(view, selected ? selectionBackground : idleColor(),
                0xff62d9ca, pressedColor(), hoverColor()));
    }

    private int idleColor() { return dark ? 0xff132638 : 0xffe7ecf2; }
    private int hoverColor() { return dark ? 0xff2b4a54 : 0xffd9eeeb; }
    private int pressedColor() { return dark ? 0xff335d61 : 0xffa4d9d1; }

    private void updateExpansion() {
        listScroll.setVisibility(expanded ? View.VISIBLE : View.GONE);
        expandButton.setText(expanded ? "▴" : "▾");
        expandButton.setContentDescription(expanded ? "候補一覧を閉じる" : "候補一覧を開く");
    }

    private void showSelection() {
        if (selectedIndex < 0) {
            stripScroll.scrollTo(0, 0);
            listScroll.scrollTo(0, 0);
            return;
        }
        if (selectedIndex >= strip.getChildCount()) return;
        View selected = strip.getChildAt(selectedIndex);
        stripScroll.smoothScrollTo(Math.max(0,
                selected.getLeft() - (stripScroll.getWidth() - selected.getWidth()) / 2), 0);
        if (expanded) {
            View selectedRow = list.getChildAt(selectedIndex);
            listScroll.smoothScrollTo(0, Math.max(0,
                    selectedRow.getTop() - (listScroll.getHeight() - selectedRow.getHeight()) / 2));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
