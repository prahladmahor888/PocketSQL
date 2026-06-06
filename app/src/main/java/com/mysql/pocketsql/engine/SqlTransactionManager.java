package com.mysql.pocketsql.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class SqlTransactionManager {
    private final DatabaseEngine engine;

    public SqlTransactionManager(DatabaseEngine engine) {
        this.engine = engine;
    }

    public QueryResult startTransaction() throws Exception {
        engine.checkActiveDatabase();
        
        if (engine.inTransaction) {
            commitTransaction();
        }

        engine.inTransaction = true;
        engine.txBackupDir = new File(engine.storageEngine.getDatabasesDir(), engine.activeDatabaseName + "_tx_backup");
        if (engine.txBackupDir.exists()) {
            deleteRecursive(engine.txBackupDir);
        }

        File activeDbDir = new File(engine.storageEngine.getDatabasesDir(), engine.activeDatabaseName);
        copyDir(activeDbDir, engine.txBackupDir);
        
        for (File spDir : engine.savepoints.values()) {
            if (spDir.exists()) {
                deleteRecursive(spDir);
            }
        }
        engine.savepoints.clear();

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult commitTransaction() throws Exception {
        if (!engine.inTransaction) {
            return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
        }

        engine.inTransaction = false;
        if (engine.txBackupDir != null && engine.txBackupDir.exists()) {
            deleteRecursive(engine.txBackupDir);
        }
        engine.txBackupDir = null;

        for (File spDir : engine.savepoints.values()) {
            if (spDir.exists()) {
                deleteRecursive(spDir);
            }
        }
        engine.savepoints.clear();

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult rollbackTransaction() throws Exception {
        if (!engine.inTransaction) {
            throw new Exception("Error: No active transaction");
        }

        engine.inTransaction = false;
        if (engine.txBackupDir != null && engine.txBackupDir.exists()) {
            File activeDbDir = new File(engine.storageEngine.getDatabasesDir(), engine.activeDatabaseName);
            if (activeDbDir.exists()) {
                deleteRecursive(activeDbDir);
            }
            copyDir(engine.txBackupDir, activeDbDir);
            deleteRecursive(engine.txBackupDir);
        }
        engine.txBackupDir = null;

        for (File spDir : engine.savepoints.values()) {
            if (spDir.exists()) {
                deleteRecursive(spDir);
            }
        }
        engine.savepoints.clear();

        engine.tableCache.clear();
        engine.activeSchemaJson = engine.storageEngine.readSchema(engine.activeDatabaseName);

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult createSavepoint(String name) throws Exception {
        if (!engine.inTransaction) {
            throw new Exception("Error: Savepoint can only be created within a transaction");
        }

        File spDir = new File(engine.storageEngine.getDatabasesDir(), engine.activeDatabaseName + "_sp_" + name);
        if (spDir.exists()) {
            deleteRecursive(spDir);
        }

        File activeDbDir = new File(engine.storageEngine.getDatabasesDir(), engine.activeDatabaseName);
        copyDir(activeDbDir, spDir);
        engine.savepoints.put(name, spDir);

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult rollbackToSavepoint(String name) throws Exception {
        if (!engine.inTransaction) {
            throw new Exception("Error: Rollback to savepoint can only be performed within a transaction");
        }
        if (!engine.savepoints.containsKey(name)) {
            throw new Exception("Error: Savepoint '" + name + "' does not exist");
        }

        File spDir = engine.savepoints.get(name);
        if (spDir.exists()) {
            File activeDbDir = new File(engine.storageEngine.getDatabasesDir(), engine.activeDatabaseName);
            if (activeDbDir.exists()) {
                deleteRecursive(activeDbDir);
            }
            copyDir(spDir, activeDbDir);
        }

        engine.tableCache.clear();
        engine.activeSchemaJson = engine.storageEngine.readSchema(engine.activeDatabaseName);

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public void copyDir(File src, File dest) throws Exception {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    copyDir(new File(src, child), new File(dest, child));
                }
            }
        } else {
            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }
        }
    }

    public void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        f.delete();
    }
}
