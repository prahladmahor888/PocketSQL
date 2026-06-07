package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SqlApiHelper {
    private static DatabaseEngine engine;
    private static SqlApiKeyManager apiKeyManager;
    private static SqlApiServer apiServer;

    public static synchronized void init(Context context) {
        if (engine == null) {
            Context appCtx = context.getApplicationContext();
            File filesDir = appCtx.getFilesDir();
            
            // Migrate old public external storage files to private internal storage
            migrateExternalToInternal(appCtx, filesDir);

            File pocketsqlDir = new File(filesDir, "PocketSQL");
            if (!pocketsqlDir.exists()) {
                pocketsqlDir.mkdirs();
            }

            // Ensure any existing database/JSON files inside internal storage are encrypted
            encryptExistingPlaintextFiles(pocketsqlDir);

            engine = new DatabaseEngine(pocketsqlDir);
            apiKeyManager = new SqlApiKeyManager(pocketsqlDir);
            apiKeyManager.initializeDefaultKey();
            apiServer = new SqlApiServer(engine, apiKeyManager);
        }
    }

    private static void migrateExternalToInternal(Context context, File internalBaseDir) {
        try {
            File extFiles = context.getExternalFilesDir(null);
            if (extFiles == null) {
                return;
            }
            File srcDir = new File(extFiles, "PocketSQL");
            if (!srcDir.exists() || !srcDir.isDirectory()) {
                return;
            }
            File destDir = new File(internalBaseDir, "PocketSQL");
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            copyDirectory(srcDir, destDir);
            deleteDirectory(srcDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void copyDirectory(File source, File destination) throws java.io.IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    copyDirectory(new File(source, file), new File(destination, file));
                }
            }
        } else {
            String name = source.getName();
            if (name.endsWith(".pqsql") || name.endsWith(".json")) {
                try {
                    copyWithEncryption(source, destination);
                } catch (Exception e) {
                    rawCopy(source, destination);
                }
            } else {
                rawCopy(source, destination);
            }
        }
    }

    private static void rawCopy(File source, File destination) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(destination)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private static void copyWithEncryption(File source, File destination) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(source);
             java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedReader reader = new java.io.BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String content = sb.toString();
        String plainText;
        try {
            plainText = SecurityHelper.decrypt(content);
        } catch (Exception e) {
            plainText = content;
        }
        String encrypted = SecurityHelper.encrypt(plainText);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(destination);
             java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedWriter writer = new java.io.BufferedWriter(osw)) {
            writer.write(encrypted);
        }
    }

    private static void encryptExistingPlaintextFiles(File directory) {
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                encryptExistingPlaintextFiles(file);
            } else {
                String name = file.getName();
                if (name.endsWith(".pqsql") || name.endsWith(".json")) {
                    try {
                        encryptFileIfNeeded(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static void encryptFileIfNeeded(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedReader reader = new java.io.BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String content = sb.toString().trim();
        if (content.isEmpty()) return;
        
        try {
            SecurityHelper.decrypt(content);
        } catch (Exception e) {
            String encrypted = SecurityHelper.encrypt(content);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                 java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8);
                 java.io.BufferedWriter writer = new java.io.BufferedWriter(osw)) {
                writer.write(encrypted);
            }
        }
    }

    private static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }

    public static DatabaseEngine getEngine() {
        return engine;
    }

    public static SqlApiKeyManager getApiKeyManager() {
        return apiKeyManager;
    }

    public static SqlApiServer getApiServer() {
        return apiServer;
    }

    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "localhost";
    }
}
