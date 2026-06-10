package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.File;
import java.security.SecureRandom;
import java.util.Base64;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class SqlCipherHelper {
    private static boolean libsLoaded = false;
    private static String cachedPassphrase = null;

    public static synchronized void init(Context context) {
        if (!libsLoaded) {
            System.loadLibrary("sqlcipher");
            libsLoaded = true;
        }
    }

    public static synchronized String getOrGeneratePassphrase(Context context) {
        if (cachedPassphrase != null) {
            return cachedPassphrase;
        }
        File keyFile = new File(context.getFilesDir(), "sqlcipher_key.enc");
        if (keyFile.exists()) {
            try {
                byte[] encoded = java.nio.file.Files.readAllBytes(keyFile.toPath());
                String encrypted = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
                cachedPassphrase = SecurityHelper.decrypt(encrypted);
                return cachedPassphrase;
            } catch (Exception e) {
                // Ignore and fall through to generate
            }
        }
        // Generate new random passphrase
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        cachedPassphrase = Base64.getEncoder().encodeToString(bytes);
        try {
            String encrypted = SecurityHelper.encrypt(cachedPassphrase);
            java.nio.file.Files.write(keyFile.toPath(), encrypted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            SqlLog.printStackTrace(e);
        }
        return cachedPassphrase;
    }

    public static SQLiteDatabase openOrCreateDatabase(File file, String passphrase) {
        return SQLiteDatabase.openOrCreateDatabase(file.getAbsolutePath(), passphrase, null, null);
    }

    public static SQLiteDatabase openDatabase(String path, String passphrase) {
        return SQLiteDatabase.openDatabase(path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null);
    }
}
