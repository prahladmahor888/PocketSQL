package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SqlApiHelper {
    private static Context context;
    private static DatabaseEngine engine;
    private static SqlApiKeyManager apiKeyManager;
    private static SqlApiServer apiServer;
    private static volatile boolean isDefaultDbReady = false;
    private static volatile boolean isAllDatabasesReady = false;

    // Setup Progress Tracking
    private static volatile int setupProgressPercent = 0;
    private static volatile String currentSettingUpDb = "";
    private static volatile String setupStatusMessage = "Initializing PocketSQL...";
    private static volatile int estimatedSecondsRemaining = 0;

    public interface DatabaseSetupListener {
        void onSetupProgress(String currentDb, int currentDbIndex, int totalDbs, int progressPercent, String statusMessage, int estimatedSecondsRemaining);
        void onSetupCompleted();
    }

    private static final List<DatabaseSetupListener> setupListeners = new CopyOnWriteArrayList<>();

    public static void addSetupListener(DatabaseSetupListener listener) {
        if (listener != null) {
            setupListeners.add(listener);
            if (isAllDatabasesReady) {
                listener.onSetupCompleted();
            } else {
                listener.onSetupProgress(currentSettingUpDb, 0, 0, setupProgressPercent, setupStatusMessage, estimatedSecondsRemaining);
            }
        }
    }

    public static void removeSetupListener(DatabaseSetupListener listener) {
        if (listener != null) {
            setupListeners.remove(listener);
        }
    }

    public static boolean isDefaultDbReady() {
        return isAllDatabasesReady();
    }

    public static boolean isAllDatabasesReady() {
        if (isAllDatabasesReady) return true;
        if (engine != null) {
            try {
                String activeDb = SqlEnvConfig.getDefaultActiveDb();
                if (engine.getStorageEngine() != null && engine.getStorageEngine().databaseExists(activeDb)) {
                    isAllDatabasesReady = true;
                    isDefaultDbReady = true;
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static int getSetupProgressPercent() {
        return setupProgressPercent;
    }

    public static String getSetupStatusMessage() {
        return setupStatusMessage;
    }

    public static int getEstimatedSecondsRemaining() {
        return estimatedSecondsRemaining;
    }

    public static Context getContext() {
        return context;
    }

    public static synchronized void init(Context ctx) {
        if (engine == null) {
            context = ctx.getApplicationContext();
            
            // Initialize .env configuration
            SqlEnvConfig.init(context);

            File filesDir = context.getFilesDir();
            File pocketsqlDir = new File(filesDir, "PocketSQL");
            if (!pocketsqlDir.exists()) {
                pocketsqlDir.mkdirs();
            }

            engine = new DatabaseEngine(pocketsqlDir);
            apiKeyManager = new SqlApiKeyManager(pocketsqlDir);
            apiKeyManager.initializeDefaultKey();
            apiServer = new SqlApiServer(engine, apiKeyManager);

            if (!engine.hasUsersConfigured()) {
                engine.initializeDefaultRootUser();
            }

            // Check if active database exists on disk already
            String activeDb = SqlEnvConfig.getDefaultActiveDb();
            if (engine.getStorageEngine() != null && engine.getStorageEngine().databaseExists(activeDb)) {
                try {
                    engine.useDatabase(activeDb);
                } catch (Exception ignored) {}
                isDefaultDbReady = true;
                isAllDatabasesReady = true;
            }
        }
    }

    private static void notifyProgress(String dbName, int currentIndex, int total, int percent, String msg, int remainingSec) {
        currentSettingUpDb = dbName;
        setupProgressPercent = percent;
        setupStatusMessage = msg;
        estimatedSecondsRemaining = remainingSec;

        SqlThreadScheduler.runOnMainThread(() -> {
            for (DatabaseSetupListener listener : setupListeners) {
                try {
                    listener.onSetupProgress(dbName, currentIndex, total, percent, msg, remainingSec);
                } catch (Throwable t) {
                    SqlLog.printStackTrace(t);
                }
            }
        });
    }

    private static void notifyCompleted() {
        isAllDatabasesReady = true;
        isDefaultDbReady = true;
        setupProgressPercent = 100;
        setupStatusMessage = "PocketSQL database setup ready (100%).";
        estimatedSecondsRemaining = 0;

        SqlThreadScheduler.runOnMainThread(() -> {
            for (DatabaseSetupListener listener : setupListeners) {
                try {
                    listener.onSetupCompleted();
                } catch (Throwable t) {
                    SqlLog.printStackTrace(t);
                }
            }
        });
    }

    /**
     * Initializes the user-selected databases on background thread with live progress updates.
     * Guaranteed to install the default active database ('ecommerce') even if omitted.
     */
    public static void initializeSelectedDatabases(List<String> selectedDatabases, DatabaseSetupListener customListener) {
        if (customListener != null) {
            addSetupListener(customListener);
        }

        final List<String> toLoad = new ArrayList<>();
        if (selectedDatabases != null) {
            for (String db : selectedDatabases) {
                if (db != null && !db.trim().isEmpty() && !toLoad.contains(db.trim())) {
                    toLoad.add(db.trim());
                }
            }
        }

        final String defaultActiveDb = SqlEnvConfig.getDefaultActiveDb();
        // Mandatory default database
        if (!toLoad.contains(defaultActiveDb)) {
            toLoad.add(0, defaultActiveDb);
        }

        // Filter out already installed databases
        final List<String> pendingLoad = new ArrayList<>();
        for (String dbName : toLoad) {
            try {
                if (!engine.getStorageEngine().databaseExists(dbName)) {
                    pendingLoad.add(dbName);
                }
            } catch (Exception ignored) {
                pendingLoad.add(dbName);
            }
        }

        if (pendingLoad.isEmpty()) {
            try {
                engine.useDatabase(defaultActiveDb);
            } catch (Exception ignored) {}
            isDefaultDbReady = true;
            isAllDatabasesReady = true;
            notifyCompleted();
            return;
        }

        // Ensure defaultActiveDb is processed first
        if (pendingLoad.contains(defaultActiveDb)) {
            pendingLoad.remove(defaultActiveDb);
            pendingLoad.add(0, defaultActiveDb);
        }

        isDefaultDbReady = false;
        isAllDatabasesReady = false;

        final int totalDatabases = pendingLoad.size();
        final long setupStartTime = System.currentTimeMillis();

        notifyProgress(pendingLoad.get(0), 1, totalDatabases, 5, "Preparing database setup...", totalDatabases * 3);

        SqlThreadScheduler.runDatabaseInitTask(new Runnable() {
            @Override
            public void run() {
                final String prevUser = engine.getCurrentUser();
                final String prevHost = engine.getCurrentHost();
                engine.setCurrentUser(SecurityHelper.getDefaultUser(), SecurityHelper.getDefaultHost());

                try {
                    engine.setDeferWrite(true);
                    engine.setConstraintsEnabled(false);

                    for (int i = 0; i < pendingLoad.size(); i++) {
                        final String dbName = pendingLoad.get(i);
                        final int currentDbIndex = i + 1;
                        final float dbWeight = 90.0f / totalDatabases;
                        final float schemaWeight = dbWeight * 0.20f;
                        final float seedWeight = dbWeight * 0.80f;
                        final float startDbProgress = 5.0f + (i * dbWeight);
                        final float startSeedProgress = startDbProgress + schemaWeight;
                        final int defaultRemainingSec = Math.max(1, (totalDatabases - i) * 3);

                        final float[] currentScriptProgress = new float[]{0.0f};

                        notifyProgress(
                            dbName,
                            currentDbIndex,
                            totalDatabases,
                            (int) startDbProgress,
                            "Creating schema for " + dbName + " (" + currentDbIndex + "/" + totalDatabases + ")...",
                            defaultRemainingSec
                        );

                        // Attach real-time row insertion listener
                        engine.setRowProgressListener((databaseName, tableName, currentRow, totalRowsInBatch) -> {
                            float rowFraction = totalRowsInBatch > 0 ? ((float) currentRow / totalRowsInBatch) : 0f;
                            float effectiveScriptPercent = Math.min(100f, currentScriptProgress[0] + (rowFraction * 8.0f));
                            int overallProgress = (int) (startSeedProgress + (effectiveScriptPercent * seedWeight / 100.0f));
                            overallProgress = Math.min(98, Math.max(5, overallProgress));

                            long elapsed = System.currentTimeMillis() - setupStartTime;
                            int secLeft = overallProgress > 5 && elapsed > 200 
                                ? Math.max(1, (int) Math.round(((100.0f - overallProgress) / ((float) overallProgress / elapsed)) / 1000.0))
                                : defaultRemainingSec;

                            notifyProgress(
                                dbName,
                                currentDbIndex,
                                totalDatabases,
                                overallProgress,
                                "Inserting " + dbName + " -> " + tableName + " (row " + currentRow + "/" + totalRowsInBatch + ")...",
                                secLeft
                            );
                        });

                        java.io.InputStream schemaStream = null;
                        java.io.InputStream seedStream = null;
                        try {
                            schemaStream = context.getAssets().open("databases/" + dbName + "/schema.sql");
                            SqlScriptRunner.runScript(engine, schemaStream, null, new SqlScriptRunner.ScriptProgressListener() {
                                @Override
                                public void onStatementExecuted(String targetDb, int currentStmt, int scriptPercent, String statementSample) {
                                    int schemaProgress = (int) (startDbProgress + (scriptPercent * schemaWeight / 100.0f));
                                    schemaProgress = Math.min(98, Math.max(5, schemaProgress));
                                    String objectHint = parseSchemaObjectHint(statementSample);

                                    long elapsed = System.currentTimeMillis() - setupStartTime;
                                    int secLeft = schemaProgress > 5 && elapsed > 200 
                                        ? Math.max(1, (int) Math.round(((100.0f - schemaProgress) / ((float) schemaProgress / elapsed)) / 1000.0))
                                        : defaultRemainingSec;

                                    notifyProgress(
                                        dbName,
                                        currentDbIndex,
                                        totalDatabases,
                                        schemaProgress,
                                        "Creating schema: " + dbName + " -> " + objectHint + " (" + schemaProgress + "%)...",
                                        secLeft
                                    );
                                }
                            });

                            seedStream = context.getAssets().open("databases/" + dbName + "/seed.sql");
                            SqlScriptRunner.runScript(engine, seedStream, null, new SqlScriptRunner.ScriptProgressListener() {
                                @Override
                                public void onStatementExecuted(String targetDb, int currentStmt, int scriptPercent, String statementSample) {
                                    currentScriptProgress[0] = (float) scriptPercent;
                                    int overallProgress = (int) (startSeedProgress + (scriptPercent * seedWeight / 100.0f));
                                    overallProgress = Math.min(98, Math.max(5, overallProgress));

                                    String tableHint = "";
                                    if (statementSample != null && statementSample.toUpperCase().contains("INSERT INTO ")) {
                                        try {
                                            String upper = statementSample.toUpperCase();
                                            int idx = upper.indexOf("INSERT INTO ");
                                            String sub = statementSample.substring(idx + 12).trim();
                                            int endIdx = sub.indexOf(' ');
                                            if (endIdx < 0) endIdx = sub.indexOf('(');
                                            if (endIdx > 0) {
                                                tableHint = " -> " + sub.substring(0, endIdx).replace("`", "").trim();
                                            }
                                        } catch (Exception ex) {}
                                    }

                                    long elapsed = System.currentTimeMillis() - setupStartTime;
                                    int secLeft = overallProgress > 5 && elapsed > 200 
                                        ? Math.max(1, (int) Math.round(((100.0f - overallProgress) / ((float) overallProgress / elapsed)) / 1000.0))
                                        : defaultRemainingSec;

                                    notifyProgress(
                                        dbName,
                                        currentDbIndex,
                                        totalDatabases,
                                        overallProgress,
                                        "Seeding " + dbName + tableHint + " (" + overallProgress + "%)...",
                                        secLeft
                                    );
                                }
                            });

                        } catch (Exception e) {
                            SqlLog.printStackTrace(e);
                        } finally {
                            engine.setRowProgressListener(null);
                            if (schemaStream != null) {
                                try { schemaStream.close(); } catch (Exception ignored) {}
                            }
                            if (seedStream != null) {
                                try { seedStream.close(); } catch (Exception ignored) {}
                            }
                        }

                        // Persist tables for this database immediately to save memory
                        try {
                            engine.saveDirtyTables();
                        } catch (Exception e) {
                            SqlLog.printStackTrace(e);
                        }
                    }

                } catch (Exception e) {
                    SqlLog.printStackTrace(e);
                } finally {
                    engine.setDeferWrite(false);
                    engine.setConstraintsEnabled(true);
                    try {
                        engine.useDatabase(defaultActiveDb);
                    } catch (Exception e) {
                        SqlLog.printStackTrace(e);
                    }
                    engine.setCurrentUser(prevUser, prevHost);
                    isAllDatabasesReady = true;
                    isDefaultDbReady = true;
                    notifyCompleted();
                }
            }
        });
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

    private static String parseSchemaObjectHint(String sql) {
        if (sql == null) return "structure";
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();
        try {
            if (upper.startsWith("CREATE TABLE")) {
                String sub = trimmed.substring(12).trim();
                if (sub.toUpperCase().startsWith("IF NOT EXISTS")) {
                    sub = sub.substring(13).trim();
                }
                int parenIdx = sub.indexOf('(');
                int spaceIdx = sub.indexOf(' ');
                int endIdx = parenIdx > 0 ? parenIdx : (spaceIdx > 0 ? spaceIdx : sub.length());
                if (endIdx > 0 && endIdx < sub.length()) {
                    sub = sub.substring(0, endIdx);
                }
                String tbl = sub.replace("`", "").trim();
                return "table '" + tbl + "'";
            } else if (upper.startsWith("CREATE INDEX") || upper.startsWith("CREATE UNIQUE INDEX")) {
                int onIdx = upper.indexOf(" ON ");
                if (onIdx > 0) {
                    String sub = trimmed.substring(onIdx + 4).trim();
                    int parenIdx = sub.indexOf('(');
                    int spaceIdx = sub.indexOf(' ');
                    int endIdx = parenIdx > 0 ? parenIdx : (spaceIdx > 0 ? spaceIdx : sub.length());
                    if (endIdx > 0 && endIdx < sub.length()) {
                        sub = sub.substring(0, endIdx);
                    }
                    String tbl = sub.replace("`", "").trim();
                    return "index on '" + tbl + "'";
                }
                return "index";
            } else if (upper.startsWith("CREATE VIEW")) {
                String sub = trimmed.substring(11).trim();
                if (sub.toUpperCase().startsWith("IF NOT EXISTS")) {
                    sub = sub.substring(13).trim();
                }
                int spaceIdx = sub.indexOf(' ');
                int endIdx = spaceIdx > 0 ? spaceIdx : sub.length();
                String view = sub.substring(0, endIdx).replace("`", "").trim();
                return "view '" + view + "'";
            } else if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) {
                return "database";
            }
        } catch (Exception ignored) {}
        return "table";
    }
}
