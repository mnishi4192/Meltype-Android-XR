// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Installs the verified APK asset into Android-only, non-backed-up storage. */
public final class ModelStore {
    private static final String NAME = "zenz-v3.2-small-Q5_K_M.gguf";
    private static final long SIZE = 73_871_936L;
    private static final String SHA256 = "29c223d4c23327b80fd13ebb5ab2555057a46317997d5da391584ffbef0db673";

    private ModelStore() {}

    public static File prepare(Context context) throws IOException {
        File directory = new File(context.getNoBackupFilesDir(), "models");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create model storage");
        File model = new File(directory, NAME);
        if (verified(model)) return model;
        File temporary = new File(directory, NAME + ".part");
        try {
            try (InputStream input = context.getAssets().open("models/" + NAME);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
                output.getFD().sync();
            }
            if (!verified(temporary)) throw new IOException("Bundled model verification failed");
            if (model.exists() && !model.delete()) throw new IOException("Cannot replace invalid model");
            if (!temporary.renameTo(model)) throw new IOException("Cannot install model");
            return model;
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    private static boolean verified(File file) throws IOException {
        if (!file.isFile() || file.length() != SIZE) return false;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return SHA256.contentEquals(hash);
    }
}
