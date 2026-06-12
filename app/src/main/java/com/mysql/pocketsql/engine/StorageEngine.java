package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StorageEngine {
    private final File baseDir;
    private final File databasesDir;

    public StorageEngine(File baseDir) {
        this.baseDir = baseDir;
        this.databasesDir = new File(baseDir, "databases");
        if (!databasesDir.exists()) {
            databasesDir.mkdirs();
        }
    }

    public List<String> listDatabases() {
        List<String> list = new ArrayList<>();
        File[] dirs = databasesDir.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    list.add(dir.getName());
                }
            }
        }
        boolean hasInfoSchema = false;
        boolean hasPocketSql = false;
        boolean hasSys = false;
        for (String db : list) {
            if ("information_schema".equalsIgnoreCase(db)) hasInfoSchema = true;
            if ("pocketsql".equalsIgnoreCase(db)) hasPocketSql = true;
            if ("sys".equalsIgnoreCase(db)) hasSys = true;
        }
        if (!hasInfoSchema) list.add("information_schema");
        if (!hasPocketSql) list.add("pocketsql");
        if (!hasSys) list.add("sys");
        return list;
    }

    public boolean databaseExists(String dbName) {
        if (dbName != null && (dbName.equalsIgnoreCase("information_schema") ||
                               dbName.equalsIgnoreCase("pocketsql") ||
                               dbName.equalsIgnoreCase("sys"))) {
            return true;
        }
        File dir = new File(databasesDir, dbName);
        return dir.exists() && dir.isDirectory();
    }

    public void createDatabaseDir(String dbName) throws Exception {
        File dir = new File(databasesDir, dbName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new Exception("Failed to create database directory for " + dbName);
            }
            // Initialize empty schema.json
            writeSchema(dbName, new JSONObject());
        }
    }

    public void deleteDatabaseDir(String dbName) throws Exception {
        File dir = new File(databasesDir, dbName);
        if (dir.exists()) {
            deleteRecursive(dir);
        }
    }

    public JSONObject readSchema(String dbName) throws Exception {
        File schemaFile = new File(new File(databasesDir, dbName), "schema.json");
        if (!schemaFile.exists()) {
            return new JSONObject();
        }
        try {
            String content = readFileContent(schemaFile);
            return new JSONObject(content);
        } catch (Exception e) {
            SqlLog.err("Failed to read schema for " + dbName + ": " + e.getMessage() + ". Deleting corrupt database.");
            deleteDatabaseDir(dbName);
            return new JSONObject();
        }
    }

    public void writeSchema(String dbName, JSONObject schemaJson) throws Exception {
        File dbFolder = new File(databasesDir, dbName);
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        File schemaFile = new File(dbFolder, "schema.json");
        writeFileContent(schemaFile, schemaJson.toString(2));
    }

    public JSONArray readTableRows(String dbName, String tableName) throws Exception {
        File tableFile = new File(new File(databasesDir, dbName), tableName + ".pqsql");
        if (!tableFile.exists()) {
            return new JSONArray();
        }
        try {
            String content = readFileContent(tableFile);
            return new JSONArray(content);
        } catch (Exception e) {
            SqlLog.err("Failed to read table rows for " + dbName + "." + tableName + ": " + e.getMessage() + ". Deleting corrupt table file.");
            tableFile.delete();
            return new JSONArray();
        }
    }

    public void writeTableRows(String dbName, String tableName, JSONArray rowsJson) throws Exception {
        File dbFolder = new File(databasesDir, dbName);
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        File tableFile = new File(dbFolder, tableName + ".pqsql");
        writeFileContent(tableFile, rowsJson.toString(2));
    }

    public void deleteTableFile(String dbName, String tableName) throws Exception {
        File tableFile = new File(new File(databasesDir, dbName), tableName + ".pqsql");
        if (tableFile.exists()) {
            tableFile.delete();
        }
    }

    public long getDatabaseSize(String dbName) {
        File dir = new File(databasesDir, dbName);
        return getFolderSize(dir);
    }

    public long getTableSize(String dbName, String tableName) {
        File tableFile = new File(new File(databasesDir, dbName), tableName + ".pqsql");
        return tableFile.exists() ? tableFile.length() : 0L;
    }

    // Helper utilities
    private long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    length += file.length();
                } else {
                    length += getFolderSize(file);
                }
            }
        }
        return length;
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private String readFileContent(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String content = sb.toString();
        return SecurityHelper.decrypt(content);
    }

    private void writeFileContent(File file, String content) throws Exception {
        String encryptedContent = SecurityHelper.encrypt(content);
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(encryptedContent);
        }
    }

    public boolean usersFileExists() {
        return new File(baseDir, "users.json").exists();
    }

    public JSONObject readUsers() throws Exception {
        File usersFile = new File(baseDir, "users.json");
        if (!usersFile.exists()) {
            return new JSONObject();
        }
        try {
            String content = readFileContent(usersFile);
            return new JSONObject(content);
        } catch (Exception e) {
            SqlLog.err("Failed to read users.json: " + e.getMessage() + ". Deleting corrupt file.");
            usersFile.delete();
            return new JSONObject();
        }
    }

    public void writeUsers(JSONObject usersJson) throws Exception {
        File usersFile = new File(baseDir, "users.json");
        writeFileContent(usersFile, usersJson.toString(2));
    }

    public File getDatabasesDir() {
        return databasesDir;
    }
}
