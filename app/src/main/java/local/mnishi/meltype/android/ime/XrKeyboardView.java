// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import local.mnishi.meltype.android.R;

/** Android click targets, symbol flicks, and held editing gestures. */
@SuppressLint("ViewConstructor") // The IME creates this view with a listener, rather than inflating it.
public final class XrKeyboardView extends LinearLayout {
    public interface Listener {
        void onText(String text);
        void onFlickText(String text);
        void onSpace();
        boolean onCursorMove(int keyCode);
        void onDelete();
        void onEnter();
    }

    private enum Field { TEXT, NUMBER, PHONE, DATE, TIME, DATETIME }
    private enum Tone { LETTER, CONTROL, ACTION }
    enum DragMode { CURSOR, DELETE }

    enum DragDirection {
        NONE(0, ""), LEFT(KeyEvent.KEYCODE_DPAD_LEFT, "←"), RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, "→"),
        UP(KeyEvent.KEYCODE_DPAD_UP, "↑"), DOWN(KeyEvent.KEYCODE_DPAD_DOWN, "↓");

        final int keyCode;
        final String arrow;

        DragDirection(int keyCode, String arrow) {
            this.keyCode = keyCode;
            this.arrow = arrow;
        }

        static boolean pastThreshold(float x, float y, float threshold) {
            return Math.max(Math.abs(x), Math.abs(y)) >= threshold;
        }

        static DragDirection at(DragMode mode, float x, float y, float threshold) {
            if (!pastThreshold(x, y, threshold)) return NONE;
            float horizontal = Math.abs(x);
            float vertical = Math.abs(y);
            if (mode == DragMode.DELETE) return x <= -threshold && horizontal >= vertical * 1.25f ? LEFT : NONE;
            if (horizontal >= vertical) return x < 0 ? LEFT : RIGHT;
            return y < 0 ? UP : DOWN;
        }
    }

    enum FlickDirection {
        TAP, UP, DOWN, CANCEL;

        static FlickDirection at(float x, float y, float threshold) {
            float horizontal = Math.abs(x);
            float vertical = Math.abs(y);
            if (horizontal < threshold && vertical < threshold) return TAP;
            if (vertical < threshold || vertical < horizontal * 1.25f) return CANCEL;
            return y < 0 ? UP : DOWN;
        }
    }

    // Every entry shares the geometry of the corresponding QWERTY key. The last row lists
    // just the three punctuation keys between the page, space and enter controls.
    private static final String[][] SYMBOLS_1 = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "%", "&", "-", "+", "(", ")"},
            {"*", "\"", "'", ":", ";", "!", "?"},
            {"/", ",", "."}
    };
    private static final String[][] SYMBOLS_2 = {
            {"=", "[", "]", "{", "}", "<", ">", "\\", "|", "^"},
            {"_", "~", "`", "@", "#", "$", "%", "&", "+"},
            {"*", "\"", "'", ":", ";", "!", "?"},
            {"-", ",", "."}
    };
    private static final int PANEL = Color.rgb(7, 18, 31);
    private static final int PANEL_TOP = Color.rgb(12, 27, 44);
    private static final int KEY = Color.rgb(19, 38, 56);
    private static final int CONTROL = Color.rgb(23, 49, 66);
    private static final int BORDER = Color.rgb(69, 96, 117);
    private static final int INK = Color.rgb(237, 247, 250);
    private static final int HOVER = Color.rgb(24, 73, 88);
    private static final int TEAL = Color.rgb(98, 217, 202);
    private static final int PRESS = Color.rgb(75, 224, 205);
    private static final int AMBER = Color.rgb(240, 186, 101);
    private static final int XR_KEY = Color.rgb(36, 88, 124);
    private static final int XR_CONTROL = Color.rgb(32, 94, 116);
    private static final int XR_BORDER = Color.rgb(139, 215, 243);
    private static final int XR_AMBER = Color.rgb(166, 107, 36);
    private static final long DRAG_REPEAT_DELAY_MS = 230;
    private static final long CURSOR_REPEAT_MS = 75;
    private static final long DELETE_REPEAT_MS = 95;
    private final Listener listener;
    private final boolean xrDisplay;
    private final LinearLayout panel;
    private final List<LinearLayout> rows = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final List<Letter> letters = new ArrayList<>();
    private final float flickThreshold;
    private final Paint flickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect flickBounds = new Rect();
    private Button flickButton;
    private int flickPointer;
    private float flickStartX;
    private float flickStartY;
    private FlickDirection flickDirection;
    private String flickPreview;
    private final Runnable dragRepeat = this::repeatDrag;
    private Button dragButton;
    private int dragPointer;
    private float dragStartX;
    private float dragStartY;
    private DragMode dragMode;
    private DragDirection dragDirection = DragDirection.NONE;
    private boolean cursorDragEnabled = true;
    private boolean deleteDragEnabled = true;
    private boolean dragMoved;
    private EditorInfo editor;
    private Field field = Field.TEXT;
    private boolean shifted;
    private boolean symbols;
    private boolean moreSymbols;
    private boolean composing;
    private boolean japanese = true;
    private Button shiftKey;
    private Button spaceKey;
    private Button enterKey;
    private int geometrySignature;

    public XrKeyboardView(Context context, Listener listener) {
        super(context);
        if (listener == null) throw new IllegalArgumentException("A keyboard listener is required");
        this.listener = listener;
        flickThreshold = Math.max(dp(14), ViewConfiguration.get(context).getScaledTouchSlop() * 1.5f);
        flickPaint.setTextAlign(Paint.Align.CENTER);
        flickPaint.setTypeface(Typeface.DEFAULT_BOLD);
        xrDisplay = context.getPackageManager().hasSystemFeature("android.software.xr.api.spatial")
                || context.getPackageManager().hasSystemFeature("android.software.xr.api.openxr");
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setMotionEventSplittingEnabled(false);
        setAccessibilityPaneTitle(getResources().getString(R.string.xr_keyboard_title));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                xrDisplay ? new int[] {Color.rgb(20, 43, 64), PANEL_TOP} : new int[] {PANEL_TOP, PANEL});
        background.setCornerRadius(dp(20));
        background.setStroke(dp(xrDisplay ? 2 : 1),
                xrDisplay ? Color.rgb(87, 155, 184) : Color.rgb(44, 77, 96));
        setBackground(background);
        panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setFocusable(false);
        panel.setMotionEventSplittingEnabled(false);
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        rebuild();
    }

    /** Called at editor start, not after every keystroke. */
    public void setEditor(EditorInfo info) {
        editor = info;
        shifted = false;
        symbols = false;
        moreSymbols = false;
        composing = false;
        int inputClass = info == null ? InputType.TYPE_CLASS_TEXT : info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info == null ? 0 : info.inputType & InputType.TYPE_MASK_VARIATION;
        field = switch (inputClass) {
            case InputType.TYPE_CLASS_NUMBER -> Field.NUMBER;
            case InputType.TYPE_CLASS_PHONE -> Field.PHONE;
            case InputType.TYPE_CLASS_DATETIME -> variation == InputType.TYPE_DATETIME_VARIATION_DATE
                    ? Field.DATE : variation == InputType.TYPE_DATETIME_VARIATION_TIME ? Field.TIME : Field.DATETIME;
            default -> Field.TEXT;
        };
        rebuild();
    }

    /** Only changes labels, preserving every existing pinch/click target. */
    public void setComposing(boolean composing) {
        if (this.composing == composing) return;
        this.composing = composing;
        updateActionLabels();
    }

    /** The service and toolbar own the mode. This method only follows their label choice. */
    public void setLanguage(boolean japanese) {
        if (this.japanese == japanese) return;
        this.japanese = japanese;
        updateActionLabels();
    }

    private void rebuild() {
        cancelGestures();
        panel.removeAllViews();
        rows.clear();
        slots.clear();
        letters.clear();
        shiftKey = null;
        spaceKey = null;
        enterKey = null;
        switch (field) {
            case NUMBER -> numberLayout();
            case PHONE -> phoneLayout();
            case DATE, TIME, DATETIME -> dateTimeLayout();
            default -> {
                if (symbols) symbolLayout();
                else alphabetLayout();
            }
        }
        geometrySignature = 0;
        updateActionLabels();
        requestLayout();
    }

    private void alphabetLayout() {
        addLetters(row(), "qwertyuiop", 0);
        LinearLayout middle = row();
        spacer(middle, 0.5f);
        addLetters(middle, "asdfghjkl", 1);
        spacer(middle, 0.5f);
        LinearLayout lower = row();
        shiftKey = key(lower, "⇧", 1.5f, Tone.CONTROL, () -> {
            shifted = !shifted;
            updateLetterLabels();
        });
        addLetters(lower, "zxcvbnm", 2);
        deleteKey(lower, 1.5f);
        textControls();
        updateLetterLabels();
    }

    private void symbolLayout() {
        symbolKeys(row(), 0);
        LinearLayout middle = row();
        spacer(middle, 0.5f);
        symbolKeys(middle, 1);
        spacer(middle, 0.5f);
        LinearLayout lower = row();
        Button page = key(lower, moreSymbols ? "123" : "記号2", 1.5f, Tone.CONTROL, () -> {
            moreSymbols = !moreSymbols;
            rebuild();
        });
        page.setContentDescription(getResources().getString(moreSymbols
                ? R.string.xr_key_symbols : R.string.xr_key_more_symbols));
        symbolKeys(lower, 2);
        deleteKey(lower, 1.5f);
        textControls();
    }

    private void textControls() {
        LinearLayout controls = row();
        Button page = key(controls, symbols ? "ABC" : "?123", 1.3f, Tone.CONTROL, () -> {
            symbols = !symbols;
            moreSymbols = false;
            shifted = false;
            rebuild();
        });
        page.setContentDescription(getResources().getString(symbols
                ? R.string.xr_key_alphabet : R.string.xr_key_symbols));
        mappedTextKey(controls, symbols ? symbolPage()[3][0] : "-", 0.9f, 3, 0);
        spaceKey = makeSpaceKey(controls, 4);
        mappedTextKey(controls, ",", 0.9f, 3, 1);
        mappedTextKey(controls, ".", 0.9f, 3, 2);
        enterKey = key(controls, "", 2, Tone.ACTION, listener::onEnter);
    }

    private void numberLayout() {
        LinearLayout first = row();
        textKeys(first, "1", "2", "3");
        deleteKey(first, 1);
        LinearLayout second = row();
        textKeys(second, "4", "5", "6");
        if (editor != null && (editor.inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0) textKey(second, "-", 1);
        else spacer(second, 1);
        LinearLayout third = row();
        textKeys(third, "7", "8", "9");
        if (editor != null && (editor.inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0) textKey(third, ".", 1);
        else spacer(third, 1);
        LinearLayout controls = row();
        textKey(controls, "0", 2);
        enterKey = key(controls, "", 2, Tone.ACTION, listener::onEnter);
    }

    private void phoneLayout() {
        LinearLayout first = row();
        textKeys(first, "1", "2", "3");
        deleteKey(first, 1);
        textKeys(row(), "4", "5", "6", "+");
        textKeys(row(), "7", "8", "9", "#");
        LinearLayout controls = row();
        textKey(controls, "*", 1);
        textKey(controls, "0", 1);
        enterKey = key(controls, "", 2, Tone.ACTION, listener::onEnter);
    }

    private void dateTimeLayout() {
        LinearLayout first = row();
        textKeys(first, "1", "2", "3");
        deleteKey(first, 1);
        LinearLayout second = row();
        textKeys(second, "4", "5", "6");
        textKey(second, field == Field.TIME ? ":" : "/", 1);
        LinearLayout third = row();
        textKeys(third, "7", "8", "9");
        if (field == Field.DATE) textKey(third, "-", 1);
        else if (field == Field.DATETIME) textKey(third, ":", 1);
        else spacer(third, 1);
        LinearLayout controls = row();
        textKey(controls, "0", 2);
        if (field == Field.DATETIME) spaceKey = makeSpaceKey(controls, 1);
        enterKey = key(controls, "", field == Field.DATETIME ? 1 : 2, Tone.ACTION, listener::onEnter);
    }

    private void addLetters(LinearLayout row, String values, int symbolRow) {
        for (int i = 0; i < values.length(); i++) {
            String lower = values.substring(i, i + 1);
            Button button = key(row, lower, 1, Tone.LETTER, () -> {
                listener.onText(shifted ? lower.toUpperCase(Locale.ROOT) : lower);
                if (shifted) {
                    shifted = false;
                    updateLetterLabels();
                }
            });
            bindFlick(button, symbolRow, i);
            letters.add(new Letter(button, lower));
        }
    }

    private String[][] symbolPage() { return moreSymbols ? SYMBOLS_2 : SYMBOLS_1; }

    private void symbolKeys(LinearLayout row, int symbolRow) {
        String[] values = symbolPage()[symbolRow];
        for (int column = 0; column < values.length; column++) {
            mappedTextKey(row, values[column], 1, symbolRow, column);
        }
    }

    private void mappedTextKey(LinearLayout row, String value, float weight, int symbolRow, int column) {
        Button button = key(row, value, weight, Tone.LETTER, () -> listener.onText(value));
        bindFlick(button, symbolRow, column);
    }

    private void bindFlick(Button button, int row, int column) {
        String up = SYMBOLS_2[row][column];
        String down = SYMBOLS_1[row][column];
        button.setOnTouchListener((view, event) -> handleFlick(button, event, up, down));
    }

    private boolean handleFlick(Button button, MotionEvent event, String up, String down) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!primaryPress(event)) return true;
            cancelGestures();
            flickButton = button;
            flickPointer = event.getPointerId(0);
            flickStartX = event.getRawX(0);
            flickStartY = event.getRawY(0);
            flickDirection = FlickDirection.TAP;
            flickButton.setPressed(true);
            requestDisallowInterceptTouchEvent(true);
        } else if (button == flickButton) {
            int pointer = event.findPointerIndex(flickPointer);
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN || pointer < 0
                    || (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                resetFlick();
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                FlickDirection direction = FlickDirection.at(event.getRawX(pointer) - flickStartX,
                        event.getRawY(pointer) - flickStartY, flickThreshold);
                if (action == MotionEvent.ACTION_UP) {
                    resetFlick();
                    switch (direction) {
                        case TAP -> button.performClick();
                        case UP -> listener.onFlickText(up);
                        case DOWN -> listener.onFlickText(down);
                        case CANCEL -> { }
                    }
                } else if (direction != flickDirection) {
                    flickDirection = direction;
                    flickPreview = switch (direction) {
                        case UP -> getResources().getString(R.string.xr_key_flick_up, up);
                        case DOWN -> getResources().getString(R.string.xr_key_flick_down, down);
                        default -> null;
                    };
                    button.setPressed(direction == FlickDirection.TAP);
                    button.setSelected(flickPreview != null);
                    invalidateFlickPreview();
                }
            }
        }
        return true;
    }

    private void resetFlick() {
        if (flickButton == null) return;
        flickButton.setPressed(false);
        flickButton.setSelected(false);
        flickButton = null;
        flickPreview = null;
        requestDisallowInterceptTouchEvent(false);
        invalidateFlickPreview();
    }

    private static boolean primaryPress(MotionEvent event) {
        int buttons = event.getButtonState();
        return !event.isFromSource(InputDevice.SOURCE_MOUSE) || buttons == 0
                || (buttons & MotionEvent.BUTTON_PRIMARY) != 0;
    }

    @SuppressLint("ClickableViewAccessibility") // handleDrag sends ordinary taps through Button.performClick().
    private void bindDrag(Button button, DragMode mode) {
        button.setOnTouchListener((view, event) -> handleDrag(button, mode, event));
    }

    void setDragOptions(boolean cursorEnabled, boolean deleteEnabled) {
        cursorDragEnabled = cursorEnabled;
        deleteDragEnabled = deleteEnabled;
        if (dragButton != null && !isDragEnabled(dragMode)) resetDrag();
    }

    private boolean isDragEnabled(DragMode mode) {
        return mode == DragMode.CURSOR ? cursorDragEnabled : deleteDragEnabled;
    }

    private boolean handleDrag(Button button, DragMode mode, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!primaryPress(event)) return true;
            cancelGestures();
            dragButton = button;
            dragPointer = event.getPointerId(0);
            dragStartX = event.getRawX(0);
            dragStartY = event.getRawY(0);
            dragMode = mode;
            button.setPressed(true);
            requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (button != dragButton) return true;
        int pointer = event.findPointerIndex(dragPointer);
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP || pointer < 0
                || (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
            resetDrag();
            return true;
        }
        float x = event.getRawX(pointer) - dragStartX;
        float y = event.getRawY(pointer) - dragStartY;
        dragMoved |= DragDirection.pastThreshold(x, y, flickThreshold);
        if (action == MotionEvent.ACTION_UP) {
            boolean tap = !dragMoved;
            resetDrag();
            if (tap) button.performClick();
        } else if (action == MotionEvent.ACTION_MOVE) {
            updateDragDirection(isDragEnabled(mode) ? DragDirection.at(mode, x, y, flickThreshold) : DragDirection.NONE);
        }
        return true;
    }

    private void updateDragDirection(DragDirection direction) {
        dragButton.setPressed(!dragMoved);
        if (dragDirection == direction) return;
        boolean startsRepeat = dragDirection == DragDirection.NONE;
        dragDirection = direction;
        dragButton.setSelected(direction != DragDirection.NONE);
        invalidateFlickPreview();
        if (direction == DragDirection.NONE) {
            removeCallbacks(dragRepeat);
        } else if (startsRepeat && dispatchDragStep() && dragButton != null && dragDirection != DragDirection.NONE) {
            // One step as soon as the gesture starts, then a short delay before repeating.
            postDelayed(dragRepeat, DRAG_REPEAT_DELAY_MS);
        }
    }

    private void repeatDrag() {
        if (!isShown() || getWindowVisibility() != View.VISIBLE) {
            resetDrag();
            return;
        }
        if (dispatchDragStep() && dragButton != null && dragDirection != DragDirection.NONE) {
            postDelayed(dragRepeat, dragMode == DragMode.DELETE ? DELETE_REPEAT_MS : CURSOR_REPEAT_MS);
        }
    }

    private boolean dispatchDragStep() {
        if (dragButton == null || dragDirection == DragDirection.NONE) return false;
        if (dragMode == DragMode.CURSOR) return listener.onCursorMove(dragDirection.keyCode);
        listener.onDelete();
        return true;
    }

    private void resetDrag() {
        if (dragButton == null) return;
        removeCallbacks(dragRepeat);
        dragButton.setPressed(false);
        dragButton.setSelected(false);
        dragButton = null;
        dragDirection = DragDirection.NONE;
        dragMoved = false;
        requestDisallowInterceptTouchEvent(false);
        invalidateFlickPreview();
    }

    void cancelGestures() {
        resetFlick();
        resetDrag();
    }

    private void invalidateFlickPreview() {
        if (getParent() instanceof View previewHost) previewHost.invalidate();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE) cancelGestures();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) cancelGestures();
    }

    @Override protected void onDetachedFromWindow() {
        cancelGestures();
        super.onDetachedFromWindow();
    }

    void drawFlickPreview(Canvas canvas, ViewGroup previewHost) {
        Button button = flickButton;
        String preview = flickPreview;
        if (dragButton != null && dragDirection != DragDirection.NONE) {
            button = dragButton;
            preview = dragMode == DragMode.DELETE ? "← ⌫" : dragDirection.arrow;
        }
        if (button == null || preview == null) return;
        // Use the frame in the row. Mapping the button's content coordinates also subtracts
        // its internal TextView scroll, which can move the preview away from the pressed key.
        View row = (View) button.getParent();
        flickBounds.set(button.getLeft(), button.getTop(), button.getRight(), button.getBottom());
        previewHost.offsetDescendantRectToMyCoords(row, flickBounds);
        float margin = dp(3);
        float bottom = flickBounds.top - margin;
        // Drawing in the whole IME panel gives the first row room above the keys.
        float availableHeight = bottom - margin;
        if (availableHeight <= 0) return;
        flickPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22, getResources().getDisplayMetrics()));
        float textHeight = flickPaint.descent() - flickPaint.ascent();
        if (textHeight > availableHeight - dp(8)) {
            flickPaint.setTextSize(flickPaint.getTextSize() * Math.max(1, availableHeight - dp(8)) / textHeight);
        }
        float width = Math.min(previewHost.getWidth() - 2 * margin,
                Math.max(dp(56), flickPaint.measureText(preview) + dp(20)));
        float height = Math.min(availableHeight, Math.max(dp(38), flickPaint.descent() - flickPaint.ascent() + dp(12)));
        float center = flickBounds.exactCenterX();
        float left = Math.max(margin, Math.min(center - width / 2, previewHost.getWidth() - width - margin));
        float top = bottom - height;
        float radius = dp(11);
        flickPaint.setStyle(Paint.Style.FILL);
        flickPaint.setColor(xrDisplay ? XR_KEY : PANEL_TOP);
        canvas.drawRoundRect(left, top, left + width, bottom, radius, radius, flickPaint);
        flickPaint.setStyle(Paint.Style.STROKE);
        flickPaint.setColor(TEAL);
        flickPaint.setStrokeWidth(dp(2));
        canvas.drawRoundRect(left, top, left + width, bottom, radius, radius, flickPaint);
        canvas.drawLine(center, bottom, center, flickBounds.top, flickPaint);
        flickPaint.setStyle(Paint.Style.FILL);
        flickPaint.setColor(Color.WHITE);
        canvas.drawText(preview, left + width / 2,
                top + (height - flickPaint.ascent() - flickPaint.descent()) / 2, flickPaint);
    }

    private void updateLetterLabels() {
        for (Letter letter : letters) {
            String text = shifted ? letter.lower.toUpperCase(Locale.ROOT) : letter.lower;
            letter.button.setText(text);
            letter.button.setContentDescription(text);
        }
        if (shiftKey != null) {
            shiftKey.setSelected(shifted);
            shiftKey.setContentDescription(getResources().getString(shifted
                    ? R.string.xr_key_shift_off : R.string.xr_key_shift));
        }
    }

    private void updateActionLabels() {
        if (spaceKey != null) {
            String text = composing ? getResources().getString(R.string.xr_key_convert)
                    : japanese ? getResources().getString(R.string.xr_key_space) : "space";
            spaceKey.setText(text);
            spaceKey.setContentDescription(text);
        }
        if (enterKey != null) {
            String text = composing ? getResources().getString(R.string.xr_key_confirm) : enterLabel();
            enterKey.setText(text);
            enterKey.setContentDescription(text);
        }
    }

    private String enterLabel() {
        if (editor != null && (editor.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            if (editor.actionLabel != null && editor.actionLabel.length() > 0) return editor.actionLabel.toString();
            int resource = switch (editor.imeOptions & EditorInfo.IME_MASK_ACTION) {
                case EditorInfo.IME_ACTION_DONE -> R.string.xr_key_done;
                case EditorInfo.IME_ACTION_GO -> R.string.xr_key_go;
                case EditorInfo.IME_ACTION_NEXT -> R.string.xr_key_next;
                case EditorInfo.IME_ACTION_PREVIOUS -> R.string.xr_key_previous;
                case EditorInfo.IME_ACTION_SEND -> R.string.xr_key_send;
                case EditorInfo.IME_ACTION_SEARCH -> R.string.xr_key_search;
                default -> 0;
            };
            if (resource != 0) return getResources().getString(resource);
        }
        return field == Field.TEXT ? getResources().getString(R.string.xr_key_newline) : "Enter";
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(false);
        row.setMotionEventSplittingEnabled(false);
        panel.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(52)));
        rows.add(row);
        return row;
    }

    private void textKeys(LinearLayout row, String... values) {
        for (String value : values) textKey(row, value, 1);
    }

    private void textKey(LinearLayout row, String value, float weight) {
        key(row, value, weight, Tone.LETTER, () -> listener.onText(value));
    }

    private void deleteKey(LinearLayout row, float weight) {
        Button delete = key(row, "⌫", weight, Tone.CONTROL, listener::onDelete);
        delete.setContentDescription(getResources().getString(R.string.xr_key_delete));
        bindDrag(delete, DragMode.DELETE);
    }

    private Button makeSpaceKey(LinearLayout row, float weight) {
        Button button = key(row, "", weight, Tone.CONTROL, listener::onSpace);
        bindDrag(button, DragMode.CURSOR);
        return button;
    }

    private Button key(LinearLayout row, String label, float weight, Tone tone, Runnable action) {
        Button button = new Button(getContext());
        button.setId(View.generateViewId());
        button.setText(label);
        button.setContentDescription(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setFocusable(false);
        button.setLongClickable(false);
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setBackgroundTintList(null);
        button.setBackground(keyBackground(tone));
        button.setTextColor(keyInk(tone));
        if (xrDisplay) button.setTypeface(Typeface.DEFAULT_BOLD);
        // Tap and accessibility ACTION_CLICK use the same primary action.
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LayoutParams(0, dp(48), weight));
        slots.add(new Slot(button, weight, tone));
        return button;
    }

    private void spacer(LinearLayout row, float weight) {
        View space = new View(getContext());
        space.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(space, new LayoutParams(0, dp(48), weight));
        slots.add(new Slot(space, weight, null));
    }

    private StateListDrawable keyBackground(Tone tone) {
        StateListDrawable states = new StateListDrawable();
        boolean action = tone == Tone.ACTION;
        states.addState(new int[] {android.R.attr.state_pressed}, shape(
                xrDisplay ? action ? Color.rgb(188, 135, 65) : Color.rgb(42, 128, 156)
                        : action ? Color.rgb(255, 233, 191) : PRESS,
                xrDisplay ? Color.rgb(219, 255, 249) : Color.rgb(181, 255, 242), xrDisplay ? 3 : 2));
        states.addState(new int[] {android.R.attr.state_hovered}, shape(
                xrDisplay ? action ? Color.rgb(173, 120, 47) : Color.rgb(40, 110, 145)
                        : action ? Color.rgb(255, 211, 142) : HOVER,
                action ? Color.WHITE : xrDisplay ? Color.rgb(143, 255, 240) : TEAL, xrDisplay ? 3 : 2));
        states.addState(new int[] {android.R.attr.state_selected}, shape(
                xrDisplay ? XR_AMBER : AMBER, Color.rgb(255, 226, 173), xrDisplay ? 3 : 2));
        states.addState(new int[0], shape(action ? xrDisplay ? XR_AMBER : AMBER
                        : tone == Tone.CONTROL ? xrDisplay ? XR_CONTROL : CONTROL : xrDisplay ? XR_KEY : KEY,
                action ? Color.rgb(255, 214, 150) : xrDisplay ? XR_BORDER : BORDER, xrDisplay ? 2 : 1));
        return states;
    }

    private ColorStateList keyInk(Tone tone) {
        if (xrDisplay) return ColorStateList.valueOf(Color.WHITE);
        return new ColorStateList(new int[][] {
                {android.R.attr.state_pressed}, {android.R.attr.state_hovered},
                {android.R.attr.state_selected}, new int[0]
        }, new int[] {PANEL, tone == Tone.ACTION ? PANEL : INK, PANEL, tone == Tone.ACTION ? PANEL : INK});
    }

    private GradientDrawable shape(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(strokeDp), stroke);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthPx = MeasureSpec.getSize(widthMeasureSpec);
        if (widthPx == 0) widthPx = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(widthPx / density);
        int screenHeightDp = getResources().getConfiguration().screenHeightDp;
        boolean shortWindow = screenHeightDp > 0 && screenHeightDp < 480;
        boolean wide = widthDp >= 700;
        int keyHeight = shortWindow ? 36 : wide ? screenHeightDp >= 800 ? 72
                : screenHeightDp >= 600 ? 64 : 56 : 48;
        int gapX = wide ? 8 : 4;
        int gapY = shortWindow ? 4 : wide ? 8 : 4;
        int padX = wide ? 8 : 4;
        int padY = shortWindow ? 4 : wide ? 8 : 6;
        int maxWidth = field == Field.TEXT ? 1120 : 640;
        int panelWidth = Math.min(dp(maxWidth), Math.max(0, widthPx - dp(padX * 2)));
        int signature = keyHeight * 10000 + gapX * 1000 + gapY * 100 + padX * 10 + padY;
        if (signature != geometrySignature) {
            geometrySignature = signature;
            setPadding(dp(padX), dp(padY), dp(padX), dp(padY));
            for (LinearLayout row : rows) {
                row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(keyHeight + gapY)));
            }
            for (Slot slot : slots) {
                LayoutParams params = new LayoutParams(0, dp(keyHeight), slot.weight);
                params.setMargins(dp(gapX) / 2, 0, dp(gapX) / 2, 0);
                slot.view.setLayoutParams(params);
                if (slot.view instanceof Button) {
                    Button button = (Button) slot.view;
                    float size = slot.tone == Tone.LETTER ? shortWindow ? 18 : wide ? 26 : 21
                            : shortWindow ? 13 : wide ? 18 : 14;
                    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
                }
            }
        }
        if (panel.getLayoutParams().width != panelWidth) {
            panel.setLayoutParams(new LayoutParams(panelWidth, LayoutParams.WRAP_CONTENT));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class Slot {
        final View view;
        final float weight;
        final Tone tone;
        Slot(View view, float weight, Tone tone) { this.view = view; this.weight = weight; this.tone = tone; }
    }

    private static final class Letter {
        final Button button;
        final String lower;
        Letter(Button button, String lower) { this.button = button; this.lower = lower; }
    }
}
