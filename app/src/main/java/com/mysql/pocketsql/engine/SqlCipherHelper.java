package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.File;
import java.security.SecureRandom;
import java.util.Base64;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class SqlCipherHelper {
    private static boolean libsLoaded = false;
    private static String cachedPw = null;

    public static synchronized void init(Context context) {
        if (!libsLoaded) {
            System.loadLibrary("sqlcipher");
            libsLoaded = true;
        }
    }

    public static synchronized String getOrGeneratePw(Context context) {
        if (cachedPw != null) {
            return cachedPw;
        }
        File keyFile = new File(context.getFilesDir(), "sqlcipher_key.enc");
        if (keyFile.exists()) {
            try {
                byte[] encoded = java.nio.file.Files.readAllBytes(keyFile.toPath());
                String encrypted = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
                cachedPw = SecurityHelper.decrypt(encrypted);
                return cachedPw;
            } catch (Exception e) {
                // Ignore and fall through to generate
            }
        }
        // Generate new random pw
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        cachedPw = Base64.getEncoder().encodeToString(bytes);
        try {
            String encrypted = SecurityHelper.encrypt(cachedPw);
            java.nio.file.Files.write(keyFile.toPath(), encrypted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            SqlLog.printStackTrace(e);
        }
        return cachedPw;
    }

    public static SQLiteDatabase openOrCreateDatabase(File file, String pw) {
        return SQLiteDatabase.openOrCreateDatabase(file.getAbsolutePath(), pw, null, null);
    }

    public static SQLiteDatabase openDatabase(String path, String pw) {
        return SQLiteDatabase.openDatabase(path, pw, null, SQLiteDatabase.OPEN_READONLY, null);
    }
}
