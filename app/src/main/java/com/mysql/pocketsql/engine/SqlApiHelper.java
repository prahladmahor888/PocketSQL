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
    private static volatile boolean isDefaultDbReady = false;

    public static boolean isDefaultDbReady() {
        if (isDefaultDbReady) return true;
        if (engine != null) {
            try {
                if (engine.getStorageEngine() != null && engine.getStorageEngine().databaseExists("ecommerce")) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }


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

            // Ensure any existing database/JSON files inside internal storage are encrypted (in background IO thread)
            SqlThreadScheduler.runBackgroundTask(() -> encryptExistingPlaintextFiles(pocketsqlDir));



            engine = new DatabaseEngine(pocketsqlDir);
            apiKeyManager = new SqlApiKeyManager(pocketsqlDir);
            apiKeyManager.initializeDefaultKey();
            apiServer = new SqlApiServer(engine, apiKeyManager);

            checkAndInitializeDefaultDatabases();
        }
    }

    private static void checkAndInitializeDefaultDatabases() {
        if (!engine.hasUsersConfigured()) {
            engine.initializeDefaultRootUser();
        }

        final String[] defaultDatabases = {"banking", "ecommerce", "school", "social"};
        final String[] checkTables = {"customers", "users", "students", "users"};

        final List<String> toLoad = new java.util.ArrayList<>();
        for (int i = 0; i < defaultDatabases.length; i++) {
            try {
                if (engine.getStorageEngine().databaseExists(defaultDatabases[i])) {
                    continue; // Already loaded on disk
                }
            } catch (Exception e) {
                SqlLog.printStackTrace(e);
            }
            toLoad.add(defaultDatabases[i]);
        }


        if (toLoad.isEmpty()) {
            try {
                engine.useDatabase("ecommerce");
            } catch (Exception e) {
                SqlLog.printStackTrace(e);
            }
            isDefaultDbReady = true;
            return;
        }

        isDefaultDbReady = false;

        SqlThreadScheduler.runDatabaseInitTask(new Runnable() {
            @Override
            public void run() {

                final String prevUser = engine.getCurrentUser();
                final String prevHost = engine.getCurrentHost();
                engine.setCurrentUser(SecurityHelper.getDefaultUser(), SecurityHelper.getDefaultHost());

                try {
                    engine.setDeferWrite(true);
                    engine.setConstraintsEnabled(false);

                    // Ensure default active database ("ecommerce") is loaded first for instant app launch
                    if (toLoad.contains("ecommerce")) {
                        toLoad.remove("ecommerce");
                        toLoad.add(0, "ecommerce");
                    }

                    for (String dbName : toLoad) {
                        java.io.InputStream schemaStream = null;
                        java.io.InputStream seedStream = null;
                        try {
                            schemaStream = context.getAssets().open("databases/" + dbName + "/schema.sql");
                            seedStream = context.getAssets().open("databases/" + dbName + "/seed.sql");
                            SqlScriptRunner.runScript(engine, schemaStream);
                            SqlScriptRunner.runScript(engine, seedStream);
                        } catch (Exception e) {
                            SqlLog.printStackTrace(e);
                        } finally {
                            if (schemaStream != null) {
                                try { schemaStream.close(); } catch (Exception ignored) {}
                            }
                            if (seedStream != null) {
                                try { seedStream.close(); } catch (Exception ignored) {}
                            }
                        }

                        // Enable app entry as soon as the active database ("ecommerce") is seeded
                        if ("ecommerce".equals(dbName)) {
                            try {
                                engine.useDatabase("ecommerce");
                            } catch (Exception e) {
                                SqlLog.printStackTrace(e);
                            }
                            isDefaultDbReady = true;
                        }
                    }

                    engine.saveDirtyTables();
                } catch (Exception e) {
                    SqlLog.printStackTrace(e);
                } finally {
                    engine.setDeferWrite(false);
                    engine.setConstraintsEnabled(true);
                    try {
                        engine.useDatabase("ecommerce");
                    } catch (Exception e) {
                        SqlLog.printStackTrace(e);
                    }
                    engine.setCurrentUser(prevUser, prevHost);
                    isDefaultDbReady = true;
                }
            }
        });

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
        
        // Only encrypt if content is plaintext JSON (starts with '{' or '[')
        if (content.startsWith("{") || content.startsWith("[")) {
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

    public static String getNetworkHostAddress() {
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
