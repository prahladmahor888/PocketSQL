package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SqlApiHelper {
    private static Context context;
    private static DatabaseEngine engine;
    private static SqlApiKeyManager apiKeyManager;
    private static SqlApiServer apiServer;

    public static Context getContext() {
        return context;
    }

    public static synchronized void init(Context ctx) {
        if (engine == null) {
            context = ctx.getApplicationContext();
            File filesDir = context.getFilesDir();

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
                        SqlLog.printStackTrace(e);
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
            SqlLog.printStackTrace(ex);
        }
        return "localhost";
    }
}
