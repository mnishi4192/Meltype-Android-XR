// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.graphics.Rect;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.FrameLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import local.mnishi.meltype.android.R;
import local.mnishi.meltype.android.core.InferenceStatus;
import local.mnishi.meltype.android.core.InputOptions;
import local.mnishi.meltype.android.core.InputOptions.PunctuationStyle;

public final class SettingsActivity extends Activity {
    private static final List<PunctuationStyle> PUNCTUATION_STYLES = Arrays.asList(
            PunctuationStyle.MARU_TEN, PunctuationStyle.PERIOD_COMMA,
            PunctuationStyle.MARU_COMMA, PunctuationStyle.PERIOD_TEN);

    private TextView runtimeStatus;
    private final Consumer<String> statusObserver = text -> runOnUiThread(() -> {
        if (runtimeStatus != null) runtimeStatus.setText(text);
    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(margin, margin * 2, margin, margin);
        content.setFocusableInTouchMode(true);
        TextView title = addText(content, R.string.app_name);
        title.setTextSize(24);
        title.setAccessibilityHeading(true);
        addText(content, R.string.setup_description);
        addButton(content, R.string.input_settings,
                view -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addButton(content, R.string.input_picker,
                view -> getSystemService(InputMethodManager.class).showInputMethodPicker());
        EditText field = new EditText(this);
        field.setId(R.id.input_test_field);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setMinLines(2);
        field.setHint(R.string.test_field);
        content.addView(field);
        addText(content, R.string.ime_switch_explanation);

        MeltypePrefs preferences = new MeltypePrefs(this);
        addKeyboardSettings(content, preferences);
        addInputSettings(content, preferences);

        addHeading(content, R.string.inference_heading);
        addText(content, R.string.inference_description);
        int[] threadCounts = {0, 1, 2, 4, 8};
        int threadSelection = 0;
        int selectedThreads = preferences.inferenceThreads();
        for (int i = 0; i < threadCounts.length; i++) {
            if (threadCounts[i] == selectedThreads) threadSelection = i;
        }
        addChoice(content, R.string.inference_threads, R.array.inference_thread_labels, threadSelection,
                index -> preferences.setInferenceThreads(threadCounts[index]));
        addText(content, R.string.inference_explanation);
        runtimeStatus = new TextView(this);
        runtimeStatus.setTextSize(16);
        runtimeStatus.setPadding(0, margin / 2, 0, margin / 2);
        content.addView(runtimeStatus);
        addText(content, R.string.keyboard_help);
        addButton(content, R.string.licenses, view -> showLicenses());

        scroll.addView(content);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            if (field.hasFocus()) field.post(() -> field.requestRectangleOnScreen(
                    new Rect(0, 0, field.getWidth(), field.getHeight()), true));
            return insets;
        });
        scroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop && field.hasFocus()) field.post(() ->
                    field.requestRectangleOnScreen(new Rect(0, 0, field.getWidth(), field.getHeight()), true));
        });
        frame.requestApplyInsets();
        content.requestFocus();
    }

    @Override protected void onStart() {
        super.onStart();
        InferenceStatus.observe(statusObserver);
    }

    @Override protected void onStop() {
        InferenceStatus.remove(statusObserver);
        super.onStop();
    }

    private void addKeyboardSettings(LinearLayout content, MeltypePrefs preferences) {
        addHeading(content, R.string.input_keyboard_operations_heading);
        addToggle(content, R.string.input_hide_keyboard_on_physical_typing, preferences.hideKeyboardOnPhysicalTyping(),
                preferences::setHideKeyboardOnPhysicalTyping);
        addToggle(content, R.string.input_space_drag_moves_cursor, preferences.spaceDragMovesCursor(),
                preferences::setSpaceDragMovesCursor);
        addToggle(content, R.string.input_backspace_drag_repeats_deletion, preferences.backspaceDragRepeatsDeletion(),
                preferences::setBackspaceDragRepeatsDeletion);
    }

    private void addInputSettings(LinearLayout content, MeltypePrefs preferences) {
        InputOptions options = preferences.inputOptions();
        addHeading(content, R.string.input_conversion_heading);
        addToggle(content, R.string.input_live_conversion, options.liveConversion, preferences::setLiveConversion);
        addHelp(content, R.string.input_live_conversion_help);
        addToggle(content, R.string.ctrl_space_toggles, preferences.ctrlSpaceToggles(), preferences::setCtrlSpaceToggles);

        addHeading(content, R.string.input_symbols_heading);
        addToggle(content, R.string.input_japanese_punctuation_after_latin, options.japanesePunctuationAfterLatin,
                preferences::setJapanesePunctuationAfterLatin);
        addToggle(content, R.string.input_commit_on_punctuation, options.commitOnPunctuation,
                preferences::setCommitOnPunctuation);
        addChoice(content, R.string.input_punctuation_style, R.array.input_punctuation_styles,
                PUNCTUATION_STYLES.indexOf(options.punctuationStyle),
                selection -> preferences.setPunctuationStyle(PUNCTUATION_STYLES.get(selection)));
        addHelp(content, R.string.input_punctuation_help);
        addToggle(content, R.string.input_prefer_middle_dot, options.preferMiddleDot, preferences::setPreferMiddleDot);
        addHelp(content, R.string.input_middle_dot_help);
        addToggle(content, R.string.input_z_shortcuts, options.zShortcuts, preferences::setZShortcuts);
        addChoice(content, R.string.input_empty_space, R.array.input_space_widths, options.preferFullWidthSpace ? 1 : 0,
                selection -> preferences.setPreferFullWidthSpace(selection == 1));
    }

    private void addHeading(LinearLayout content, int title) {
        TextView heading = addText(content, title);
        heading.setTextSize(18);
        heading.setPadding(0, dp(24), 0, dp(4));
        heading.setAccessibilityHeading(true);
    }

    private void addToggle(LinearLayout content, int title, boolean selected, Consumer<Boolean> onChange) {
        CheckBox toggle = new CheckBox(this);
        toggle.setText(title);
        toggle.setMinHeight(dp(48));
        toggle.setChecked(selected);
        toggle.setOnCheckedChangeListener((view, checked) -> onChange.accept(checked));
        content.addView(toggle);
    }

    private void addChoice(LinearLayout content, int title, int entries, int selected, IntConsumer onChange) {
        TextView label = addText(content, title);
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        spinner.setMinimumHeight(dp(48));
        label.setLabelFor(spinner.getId());
        ArrayAdapter<CharSequence> labels = ArrayAdapter.createFromResource(this, entries,
                android.R.layout.simple_spinner_item);
        labels.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(labels);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private int selection = selected;

            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == selection) return;
                selection = position;
                onChange.accept(position);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        content.addView(spinner);
    }

    private void addHelp(LinearLayout content, int text) {
        TextView help = addText(content, text);
        help.setTextSize(14);
        help.setPadding(0, dp(4), 0, dp(8));
    }

    private void showLicenses() {
        StringBuilder text = new StringBuilder();
        for (String asset : new String[] {"licenses/MELTYPE-NOTICE.txt", "licenses/zenz-v3.2-small-NOTICE.txt",
                "licenses/meltype-dictionaries-NOTICES.md", "licenses/benizara-LICENSE.txt",
                "licenses/official-android-NOTICES.md", "MOZC_NOTICES.txt",
                "licenses/zenz-v3.2-APACHE-2.0.txt", "licenses/dotnet-LICENSE.txt",
                "licenses/dotnet-nativeaot-NOTICES.txt", "licenses/native-dependencies-NOTICE.txt", "licenses/GPL-3.0.txt"}) {
            try (InputStream input = getAssets().open(asset)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
                text.append(new String(output.toByteArray(), StandardCharsets.UTF_8)).append("\n\n");
            } catch (IOException e) {
                text.append(asset).append("\n");
            }
        }
        TextView document = new TextView(this);
        document.setText(text);
        document.setTextIsSelectable(true);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        document.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(document);
        new AlertDialog.Builder(this).setTitle(R.string.licenses).setView(scroll)
                .setPositiveButton(R.string.close, null).show();
    }

    private TextView addText(LinearLayout content, int text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        content.addView(view);
        return view;
    }

    private void addButton(LinearLayout content, int title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setMinHeight(dp(48));
        button.setOnClickListener(listener);
        content.addView(button);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
