// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import local.mnishi.meltype.android.R;
import local.mnishi.meltype.android.core.CompositionView;

/** Candidate and input controls shared by the physical and on-screen keyboards. */
@SuppressLint("ViewConstructor") // Constructed by the service with an explicit action listener.
public final class CandidateBarView extends LinearLayout {
    private boolean hardwareCandidatesVisible;
    public interface Listener {
        void onCandidateSelected(int index);
        void onToggleDirect();
        void onConvert(int vk);
        void onSwitchIme();
        void onToggleKeyboard();
        void onCandidateListChanged();
        void onSettings();
    }

    private final CandidateStripView candidates;
    private final TextView mode;
    private final LinearLayout convertButtons;
    private final TextView warning;
    private final TextView preview;
    private final TextView keyboardToggle;
    private final int foreground;
    private final int buttonBackground;
    private final boolean roomy;
    private final boolean shortPanel;
    private String lastRaw = "";
    private CompositionView composition;
    private boolean previewEnabled;

    public CandidateBarView(Context context, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        Configuration config = getResources().getConfiguration();
        roomy = PanelButton.roomy(config);
        shortPanel = config.screenHeightDp > 0 && config.screenHeightDp < 480;
        boolean dark = roomy || (config.uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        foreground = dark ? Color.rgb(229, 231, 235) : Color.rgb(32, 33, 36);
        buttonBackground = dark ? Color.rgb(52, 57, 65) : Color.rgb(229, 235, 244);
        setBackgroundColor(dark ? 0xff0c1b2c : 0xfff5f7fa);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(roomy ? 12 : 4), 0, dp(roomy ? 12 : 4), 0);
        toolbar.setFocusable(false);
        int target = dp(roomy ? 60 : 48);
        addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, target));

        mode = button("あ", "日本語／英数を切り替える", listener::onToggleDirect);
        mode.setTextSize(18);
        toolbar.addView(mode, controlParams(dp(roomy ? 60 : 44), target));

        convertButtons = new LinearLayout(context);
        convertButtons.setOrientation(HORIZONTAL);
        TextView kana = button(getResources().getString(R.string.candidate_katakana),
                "カタカナに変換、長押しでひらがなに変換", () -> listener.onConvert(0x76));
        kana.setOnLongClickListener(view -> { listener.onConvert(0x75); return true; });
        convertButtons.addView(kana, controlParams(dp(60), target));
        convertButtons.addView(button(getResources().getString(R.string.candidate_alphanumeric),
                "英数に変換", () -> listener.onConvert(0x79)), controlParams(dp(60), target));
        convertButtons.setVisibility(GONE);
        toolbar.addView(convertButtons, new LayoutParams(LayoutParams.WRAP_CONTENT, target));

        String switchLabel = getResources().getString(R.string.input_picker);
        TextView switchIme = button(switchLabel, switchLabel, listener::onSwitchIme);
        switchIme.setTextSize(roomy ? 16 : 13);
        switchIme.setSingleLine(false);
        switchIme.setMaxLines(2);
        switchIme.setPadding(dp(4), 0, dp(4), 0);
        LayoutParams switchParams = controlParams(0, target);
        switchParams.weight = 1;
        toolbar.addView(switchIme, switchParams);
        keyboardToggle = button("", "", listener::onToggleKeyboard);
        keyboardToggle.setSingleLine(false);
        keyboardToggle.setMaxLines(2);
        toolbar.addView(keyboardToggle, controlParams(dp(roomy ? 144 : 92), target));
        TextView settings = button(getResources().getString(R.string.candidate_settings),
                "Meltypeの設定を開く", listener::onSettings);
        toolbar.addView(settings, controlParams(dp(roomy ? 68 : 44), target));

        warning = new TextView(context);
        warning.setTextSize(14);
        warning.setTextColor(dark ? Color.rgb(255, 199, 156) : Color.rgb(140, 55, 0));
        warning.setPadding(dp(12), dp(4), dp(12), dp(4));
        warning.setMaxLines(2);
        warning.setEllipsize(TextUtils.TruncateAt.END);
        warning.setFocusable(false);
        warning.setVisibility(GONE);
        warning.setOnClickListener(view -> listener.onSettings());
        addView(warning, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        preview = new TextView(context);
        preview.setTextSize(roomy ? 27 : 20);
        preview.setTextColor(foreground);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(16), 0, dp(16), 0);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.START);
        preview.setVisibility(GONE);
        addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, dp(roomy ? 46 : 34)));

        candidates = new CandidateStripView(context, listener::onCandidateSelected,
                expanded -> listener.onCandidateListChanged());
        addView(candidates, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void showComposition(CompositionView composition) {
        this.composition = composition;
        boolean composing = composition != null
                && (notEmpty(composition.text) || notEmpty(composition.raw));
        convertButtons.setVisibility(composing && roomy ? VISIBLE : GONE);
        updatePreview();
        if (!composing) {
            lastRaw = "";
            candidates.clear();
            return;
        }
        String raw = composition.raw == null ? "" : composition.raw;
        if (!raw.equals(lastRaw)) candidates.setExpanded(false);
        lastRaw = raw;
        candidates.setCandidates(composition.candidates == null
                ? Collections.emptyList() : composition.candidates, composition.selectedIndex);
        if (hardwareCandidatesVisible) candidates.setVisibility(GONE);
    }

    void setKeyboardVisible(boolean visible, boolean allowPreview) {
        keyboardToggle.setText(visible ? R.string.xr_hide_keyboard : R.string.xr_show_keyboard);
        String description = getResources().getString(visible ? R.string.xr_hide_keyboard_description
                : R.string.xr_show_keyboard_description);
        keyboardToggle.setContentDescription(description);
        keyboardToggle.setTooltipText(description);
        previewEnabled = visible && allowPreview && !shortPanel;
        updatePreview();
    }

    void setHardwareCandidatesVisible(boolean visible) {
        hardwareCandidatesVisible = visible;
        if (visible) candidates.setExpanded(false);
        candidates.setVisibility(!visible && composition != null && !composition.candidates.isEmpty() ? VISIBLE : GONE);
    }

    private void updatePreview() {
        boolean show = previewEnabled && composition != null && notEmpty(composition.text);
        preview.setText(show ? composition.text : "");
        preview.setVisibility(show ? VISIBLE : GONE);
    }

    public void showMode(String text) {
        mode.setText(text == null ? "あ" : text);
        mode.setContentDescription("入力モード " + mode.getText() + "、タップで日本語／英数を切り替える");
    }

    public void setRuntimeStatus(String text, boolean failed) {
        // Thread counts and runtime details belong in settings; this row only helps recover input.
        warning.setText(failed ? text : "");
        warning.setVisibility(failed ? VISIBLE : GONE);
    }

    public void setExpanded(boolean expanded) { candidates.setExpanded(expanded); }
    public boolean isExpanded() { return candidates.isExpanded(); }

    private LayoutParams controlParams(int width, int height) {
        LayoutParams params = new LayoutParams(width, height);
        if (roomy) params.setMarginEnd(dp(8));
        return params;
    }

    private TextView button(String text, String description, Runnable action) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(foreground);
        view.setTextSize(roomy ? 16 : 13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(2), 0, dp(2), 0);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setFocusable(false);
        view.setContentDescription(description);
        view.setTooltipText(description);
        boolean dark = foreground == Color.rgb(229, 231, 235);
        view.setBackground(PanelButton.background(view, buttonBackground, 0xff2ac7b3,
                dark ? 0xff335d61 : 0xffa4d9d1, dark ? 0xff2b4a54 : 0xffd9eeeb));
        view.setOnClickListener(ignored -> action.run());
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return view;
    }

    private static boolean notEmpty(String text) { return text != null && !text.isEmpty(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
