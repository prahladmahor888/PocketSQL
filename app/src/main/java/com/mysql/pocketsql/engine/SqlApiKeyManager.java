package com.mysql.pocketsql.engine;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class SqlApiKeyManager {
    private final File baseDir;
    private final File keysFile;
    private JSONObject cachedKeys;

    public SqlApiKeyManager(File baseDir) {
        this.baseDir = baseDir;
        this.keysFile = new File(baseDir, "apikeys.json");
        loadKeys();
    }

    private synchronized void loadKeys() {
        try {
            if (!keysFile.exists()) {
                cachedKeys = new JSONObject();
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(keysFile);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String content = sb.toString();
            try {
                content = SecurityHelper.decrypt(content);
            } catch (Exception e) {
                // Fallback for legacy plaintext key file
            }
            cachedKeys = new JSONObject(content);
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
            cachedKeys = new JSONObject();
        }
    }

    private synchronized void saveKeys() {
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            String content = cachedKeys.toString(2);
            String encrypted = SecurityHelper.encrypt(content);
            try (FileOutputStream fos = new FileOutputStream(keysFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                writer.write(encrypted);
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
    }

    public synchronized boolean isValidKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        return cachedKeys.has(key);
    }

    public synchronized String generateKey(String label) {
        return generateKey(label, "*");
    }

    public synchronized String generateKey(String label, String database) {
        if (label == null || label.trim().isEmpty()) {
            label = "API Key";
        }
        if (database == null || database.trim().isEmpty()) {
            database = "*";
        }
        String newKey = "psql_live_" + UUID.randomUUID().toString().replace("-", "");
        try {
            JSONObject keyData = new JSONObject();
            keyData.put("name", label);
            keyData.put("database", database.trim());
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            keyData.put("created_at", sdf.format(new Date()));
            
            cachedKeys.put(newKey, keyData);
            saveKeys();
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
        return newKey;
    }

    public synchronized String getKeyDatabase(String key) {
        if (key == null || !cachedKeys.has(key)) {
            return "*";
        }
        JSONObject data = cachedKeys.optJSONObject(key);
        if (data != null && data.has("database")) {
            String db = data.optString("database", "*").trim();
            return db.isEmpty() ? "*" : db;
        }
        return "*";
    }


    public synchronized void deleteKey(String key) {
        if (key == null) return;
        cachedKeys.remove(key);
        saveKeys();
    }

    public synchronized JSONObject getKeys() {
        return cachedKeys;
    }

    public synchronized void initializeDefaultKey() {
        if (cachedKeys.length() == 0) {
            generateKey("Default API Key");
        }
    }
}
