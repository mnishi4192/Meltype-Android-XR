// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.LinearLayout;

/** Handles navigation insets below both the candidate bar and the keyboard. */
@SuppressLint("ViewConstructor")
final class ImePanel extends LinearLayout {
    private final XrKeyboardView keyboard;

    ImePanel(Context context, CandidateBarView bar, XrKeyboardView keyboard) {
        super(context);
        this.keyboard = keyboard;
        setMotionEventSplittingEnabled(false);
        setOrientation(VERTICAL);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(keyboard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        keyboard.drawFlickPreview(canvas, this);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
        post(() -> applyNavigationInsets(getRootWindowInsets()));
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        WindowInsets result = super.onApplyWindowInsets(insets);
        WindowInsets root = getRootWindowInsets();
        applyNavigationInsets(root != null ? root : insets);
        return result;
    }

    @SuppressWarnings("deprecation")
    private void applyNavigationInsets(WindowInsets insets) {
        if (insets == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
            setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, insets.getStableInsetBottom()));
        } else {
            setPadding(insets.getStableInsetLeft(), 0, insets.getStableInsetRight(), insets.getStableInsetBottom());
        }
    }
}
