// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility entry for launcher shortcuts created by previous versions. */
public final class SetupActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, local.mnishi.meltype.android.settings.SettingsActivity.class));
        finish();
    }
}
