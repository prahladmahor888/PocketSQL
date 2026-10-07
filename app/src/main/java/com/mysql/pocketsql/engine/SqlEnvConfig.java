package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SqlEnvConfig — Manages environment variables and configuration loaded from .env file.
 * Checks internal storage PocketSQL/.env, falling back to assets/.env or built-in defaults.
 */
public class SqlEnvConfig {

    private static final Map<String, String> ENV_VARS = new HashMap<>();
    private static boolean isInitialized = false;

    // Default configuration content for newly created .env file
    private static final String DEFAULT_ENV_CONTENT = 
        "# PocketSQL Environment Configuration\n" +
        "# Default databases seeded on first launch (comma-separated)\n" +
        "DEFAULT_DATABASES=banking,ecommerce,school,social\n" +
        "# Active database selected on startup\n" +
        "DEFAULT_ACTIVE_DB=ecommerce\n" +
        "# Asynchronous background database initialization\n" +
        "ASYNC_DB_INIT=true\n" +
        "# Number of seed statements before micro-yielding CPU to UI/queries\n" +
        "DB_SEED_BATCH_YIELD=20\n" +
        "# Auto-login to active database on app launch\n" +
        "AUTO_LOGIN=true\n" +
        "# REST API Server Configuration\n" +
        "API_SERVER_ENABLED=true\n" +
        "API_SERVER_PORT=8080\n" +
        "API_SSL_PORT=8443\n" +
        "# Query Thread Priority (-8 = foreground/high priority)\n" +
        "QUERY_THREAD_PRIORITY=-8\n" +
        "# Background Init Thread Priority (10 = background/yields)\n" +
        "INIT_THREAD_PRIORITY=10\n" +
        "\n" +
        "# Android KeyStore & Cryptography Configuration\n" +
        "KEYSTORE_PROVIDER=AndroidKeyStore\n" +
        "KEYSTORE_KEY_ALIAS=PocketSQLAESKeyAlias\n" +
        "KEYSTORE_TLS_CERT_ALIAS=psql_entry\n" +
        "KEYSTORE_AES_KEY_SIZE=256\n" +
        "KEYSTORE_RSA_KEY_SIZE=2048\n" +
        "KEYSTORE_CERT_SUBJECT=CN=localhost, O=PocketSQL, C=US\n" +
        "KEYSTORE_CERT_VALIDITY_YEARS=1\n" +
        "KEYSTORE_ENCRYPTION_CIPHER=AES/GCM/NoPadding\n";

    public static synchronized void init(Context context) {
        if (isInitialized && !ENV_VARS.isEmpty()) return;

        ENV_VARS.clear();
        try {
            File baseDir = new File(context.getFilesDir(), "PocketSQL");
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }

            File envFile = new File(baseDir, ".env");
            
            // If .env doesn't exist in internal storage, check assets or create it
            if (!envFile.exists()) {
                boolean copiedFromAssets = false;
                try (InputStream is = context.getAssets().open(".env")) {
                    loadFromStream(is);
                    copiedFromAssets = true;
                    // Persist to internal storage for easy customization
                    try (FileOutputStream fos = new FileOutputStream(envFile);
                         InputStream is2 = context.getAssets().open(".env")) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is2.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                } catch (Exception ignored) {}

                if (!copiedFromAssets) {
                    // Create default .env file
                    try (FileOutputStream fos = new FileOutputStream(envFile);
                         OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                         BufferedWriter writer = new BufferedWriter(osw)) {
                        writer.write(DEFAULT_ENV_CONTENT);
                    } catch (Exception e) {
                        SqlLog.printStackTrace(e);
                    }
                }
            }

            // Read from the internal storage .env file
            if (envFile.exists()) {
                try (FileInputStream fis = new FileInputStream(envFile)) {
                    loadFromStream(fis);
                }
            }
        } catch (Exception e) {
            SqlLog.printStackTrace(e);
        }

        // Apply fallback defaults if keys missing
        applyDefaultIfMissing("DEFAULT_DATABASES", "banking,ecommerce,school,social");
        applyDefaultIfMissing("DEFAULT_ACTIVE_DB", "ecommerce");
        applyDefaultIfMissing("ASYNC_DB_INIT", "true");
        applyDefaultIfMissing("DB_SEED_BATCH_YIELD", "20");
        applyDefaultIfMissing("AUTO_LOGIN", "true");
        applyDefaultIfMissing("API_SERVER_ENABLED", "true");
        applyDefaultIfMissing("API_SERVER_PORT", "8080");
        applyDefaultIfMissing("API_SSL_PORT", "8443");
        applyDefaultIfMissing("QUERY_THREAD_PRIORITY", "-8");
        applyDefaultIfMissing("INIT_THREAD_PRIORITY", "10");

        // KeyStore defaults
        applyDefaultIfMissing("KEYSTORE_PROVIDER", "AndroidKeyStore");
        applyDefaultIfMissing("KEYSTORE_KEY_ALIAS", "PocketSQLAESKeyAlias");
        applyDefaultIfMissing("KEYSTORE_TLS_CERT_ALIAS", "psql_entry");
        applyDefaultIfMissing("KEYSTORE_AES_KEY_SIZE", "256");
        applyDefaultIfMissing("KEYSTORE_RSA_KEY_SIZE", "2048");
        applyDefaultIfMissing("KEYSTORE_CERT_SUBJECT", "CN=localhost, O=PocketSQL, C=US");
        applyDefaultIfMissing("KEYSTORE_CERT_VALIDITY_YEARS", "1");
        applyDefaultIfMissing("KEYSTORE_ENCRYPTION_CIPHER", "AES/GCM/NoPadding");

        isInitialized = true;
    }

    private static void loadFromStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String key = trimmed.substring(0, eqIdx).trim();
                    String val = trimmed.substring(eqIdx + 1).trim();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    ENV_VARS.put(key, val);
                }
            }
        } catch (Exception e) {
            SqlLog.printStackTrace(e);
        }
    }

    private static void applyDefaultIfMissing(String key, String defVal) {
        if (!ENV_VARS.containsKey(key) || ENV_VARS.get(key) == null || ENV_VARS.get(key).trim().isEmpty()) {
            ENV_VARS.put(key, defVal);
        }
    }

    public static String get(String key, String defaultValue) {
        String val = ENV_VARS.get(key);
        return val != null ? val : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String val = ENV_VARS.get(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = ENV_VARS.get(key);
        if (val != null) {
            return "true".equalsIgnoreCase(val.trim()) || "1".equals(val.trim()) || "yes".equalsIgnoreCase(val.trim());
        }
        return defaultValue;
    }

    public static List<String> getDefaultDatabases() {
        String raw = get("DEFAULT_DATABASES", "banking,ecommerce,school,social");
        List<String> list = new ArrayList<>();
        for (String s : raw.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    public static String getDefaultActiveDb() {
        return get("DEFAULT_ACTIVE_DB", "ecommerce");
    }

    public static boolean isAsyncDbInit() {
        return getBoolean("ASYNC_DB_INIT", true);
    }

    public static int getDbSeedBatchYield() {
        return getInt("DB_SEED_BATCH_YIELD", 20);
    }

    // KeyStore and Cryptography configuration getters
    public static String getKeystoreProvider() {
        return get("KEYSTORE_PROVIDER", "AndroidKeyStore");
    }

    public static String getKeystoreKeyAlias() {
        return get("KEYSTORE_KEY_ALIAS", "PocketSQLAESKeyAlias");
    }

    public static String getKeystoreTlsCertAlias() {
        return get("KEYSTORE_TLS_CERT_ALIAS", "psql_entry");
    }

    public static int getKeystoreAesKeySize() {
        return getInt("KEYSTORE_AES_KEY_SIZE", 256);
    }

    public static int getKeystoreRsaKeySize() {
        return getInt("KEYSTORE_RSA_KEY_SIZE", 2048);
    }

    public static String getKeystoreCertSubject() {
        return get("KEYSTORE_CERT_SUBJECT", "CN=localhost, O=PocketSQL, C=US");
    }

    public static int getKeystoreCertValidityYears() {
        return getInt("KEYSTORE_CERT_VALIDITY_YEARS", 1);
    }

    public static String getKeystoreEncryptionCipher() {
        return get("KEYSTORE_ENCRYPTION_CIPHER", "AES/GCM/NoPadding");
    }
}
