// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/** Updates the supported official preferences while leaving the rest of the profile intact. */
public final class OfficialCoreConfig {
    private static final int SETTINGS_VERSION = 12;

    private OfficialCoreConfig() {}

    public static void save(File directory, InputOptions options) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create Core data directory");
        AtomicFile file = new AtomicFile(new File(directory, "config.json"));
        try {
            JSONObject values;
            try { values = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8)); }
            catch (FileNotFoundException missing) {
                values = new JSONObject();
                values.put("SettingsVersion", SETTINGS_VERSION);
            }
            values.put("LiveConversion", options.liveConversion);
            values.put("JapanesePunctuationAfterEnglish", options.japanesePunctuationAfterLatin);
            values.put("Punctuation", switch (options.punctuationStyle) {
                case MARU_TEN -> "Japanese";
                case PERIOD_COMMA -> "FullWidthCommaPeriod";
                case MARU_COMMA -> "FullWidthCommaKuten";
                case PERIOD_TEN -> "ToutenFullWidthPeriod";
            });
            values.put("Slash", options.preferMiddleDot ? "MiddleDot" : "Slash");
            values.put("ZSymbols", options.zShortcuts);
            values.put("SpaceWidth", options.preferFullWidthSpace ? "FullWidth" : "Standard");
            values.put("UseZenzai", true);
            FileOutputStream output = file.startWrite();
            try {
                output.write(values.toString(2).getBytes(StandardCharsets.UTF_8));
                file.finishWrite(output);
            } catch (IOException failure) {
                file.failWrite(output);
                throw failure;
            }
        } catch (JSONException malformedConfig) {
            throw new IOException("Cannot update official Core configuration", malformedConfig);
        }
    }
}
