// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

final class PanelButton {
    private PanelButton() {}

    static boolean roomy(Configuration config) {
        return config.screenWidthDp >= 700 && config.screenHeightDp >= 480;
    }

    static StateListDrawable background(View view, int idle, int active, int pressed, int hover) {
        float density = view.getResources().getDisplayMetrics().density;
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, shape(pressed, Color.TRANSPARENT, density));
        background.addState(new int[]{android.R.attr.state_hovered}, shape(hover, active, density));
        background.addState(new int[]{android.R.attr.state_focused}, shape(hover, active, density));
        background.addState(new int[0], shape(idle, Color.TRANSPARENT, density));
        return background;
    }

    private static GradientDrawable shape(int color, int border, float density) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(12 * density);
        shape.setColor(color);
        shape.setStroke(Math.max(1, Math.round(2 * density)), border);
        return shape;
    }
}
