package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DatabaseExporter {

    public static void exportDatabase(DatabaseEngine engine, String dbName, OutputStream os, String format) throws Exception {
        engine.saveDirtyTables();
        if ("db".equalsIgnoreCase(format) || "zip".equalsIgnoreCase(format)) {
            if (isAndroid() && "db".equalsIgnoreCase(format)) {
                exportSQLite(engine, dbName, os);
            } else {
                exportDbZip(engine, dbName, os);
            }
        } else if ("sql".equalsIgnoreCase(format)) {
            exportSql(engine, dbName, os);
        } else if ("csv".equalsIgnoreCase(format)) {
            exportCsvZip(engine, dbName, os);
        } else if ("xlsx".equalsIgnoreCase(format) || "xls".equalsIgnoreCase(format) || "xml".equalsIgnoreCase(format)) {
            exportXlsx(engine, dbName, os);
        } else {
            throw new Exception("Unsupported export format: " + format);
        }
    }

    public static void importDatabase(DatabaseEngine engine, String dbName, InputStream is, String formatHint) throws Exception {
        // Wrap InputStream in a pushback or read entirely into memory if we need signature sniffing.
        // But since we can snout zip files, let's read the first few bytes.
        byte[] header = new byte[4];
        is.mark(1024); // Support marking if possible, otherwise read to byte array
        // We'll copy stream into memory or read into a byte array if mark not supported
        byte[] data = readAllBytes(is);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        boolean isZip = data.length >= 4 && data[0] == 0x50 && data[1] == 0x4B && data[2] == 0x03 && data[3] == 0x04;
        byte[] sqliteMagic = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
        boolean isSqlite = data.length >= 16;
        if (isSqlite) {
            for (int i = 0; i < sqliteMagic.length; i++) {
                if (data[i] != sqliteMagic[i]) {
                    isSqlite = false;
                    break;
                }
            }
        }

        if (isSqlite) {
            if (isAndroid()) {
                importSQLite(engine, dbName, new ByteArrayInputStream(data));
            } else {
                throw new Exception("SQLite import is only supported on Android");
            }
        } else if (isZip) {
            // Check if it has schema.json -> Native PocketSQL DB backup
            // or xl/workbook.xml -> Standard Excel .xlsx
            boolean hasSchemaJson = false;
            boolean hasWorkbookXml = false;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if ("schema.json".equals(name)) {
                        hasSchemaJson = true;
                        break;
                    } else if ("xl/workbook.xml".equalsIgnoreCase(name)) {
                        hasWorkbookXml = true;
                    }
                }
            }
            if (hasSchemaJson) {
                importDbZip(engine, dbName, new ByteArrayInputStream(data));
            } else if (hasWorkbookXml) {
                importXlsx(engine, dbName, data);
            } else {
                importCsvZip(engine, dbName, new ByteArrayInputStream(data));
            }
        } else {
            // Text based. Sniff XML or SQL
            String content = new String(data, StandardCharsets.UTF_8);
            String trimmed = content.trim();
            String upper = trimmed.toUpperCase();
            
            boolean isSql = "sql".equalsIgnoreCase(formatHint) ||
                            upper.startsWith("CREATE DATABASE") ||
                            upper.startsWith("USE") ||
                            upper.startsWith("CREATE TABLE") ||
                            upper.contains("INSERT INTO") ||
                            upper.contains("DROP TABLE") ||
                            upper.contains("CREATE VIEW") ||
                            upper.contains("CREATE FUNCTION") ||
                            upper.contains("CREATE TRIGGER") ||
                            upper.contains("CREATE EVENT") ||
                            upper.contains("DELIMITER");
            
            if (trimmed.startsWith("<?xml") || trimmed.contains("<Workbook")) {
                importSpreadsheetML(engine, dbName, new ByteArrayInputStream(data));
            } else if (isSql) {
                importSql(engine, dbName, new ByteArrayInputStream(data));
            } else {
                // Default to single CSV import
                importSingleCsv(engine, dbName, dbName, new ByteArrayInputStream(data));
            }
        }
    }

    // ── NATIVE BACKUP (.db / .zip) ──────────────────────────────────────────

    private static void exportDbZip(DatabaseEngine engine, String dbName, OutputStream os) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        File dbFolder = new File(storage.getDatabasesDir(), dbName);
        if (!dbFolder.exists()) {
            throw new Exception("Database '" + dbName + "' does not exist");
        }
        try (ZipOutputStream zos = new ZipOutputStream(os)) {
            File[] files = dbFolder.listFiles();
            if (files != null) {
                byte[] buffer = new byte[4096];
                for (File file : files) {
                    if (file.isFile()) {
                        ZipEntry entry = new ZipEntry(file.getName());
                        zos.putNextEntry(entry);
                        try (FileInputStream fis = new FileInputStream(file)) {
                            int len;
                            while ((len = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
        }
    }

    private static void importDbZip(DatabaseEngine engine, String dbName, InputStream is) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        File dbFolder = new File(storage.getDatabasesDir(), dbName);
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        byte[] buffer = new byte[4096];
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File outFile = new File(dbFolder, name);
                if (!outFile.getCanonicalPath().startsWith(dbFolder.getCanonicalPath())) {
                    throw new Exception("Security Error: Zip Slip detected in entry " + name);
                }
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
        if (dbName.equalsIgnoreCase(engine.getActiveDatabase())) {
            engine.useDatabase(dbName);
        }
    }

    // ── SQL SCRIPT (.sql) ──────────────────────────────────────────────────

    private static void exportSql(DatabaseEngine engine, String dbName, OutputStream os) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        JSONObject schemaJson = storage.readSchema(dbName);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            writer.write("-- PocketSQL Database Dump\n");
            writer.write("-- Database: " + dbName + "\n");
            writer.write("-- ------------------------------------------------------\n\n");
            writer.write("SET FOREIGN_KEY_CHECKS = 0;\n\n");
            writer.write("CREATE DATABASE IF NOT EXISTS " + dbName + ";\n");
            writer.write("USE " + dbName + ";\n\n");

            // 1. Export Tables
            Iterator<String> keys = schemaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("__") && key.endsWith("__")) continue; // internal system keys
                JSONObject ts = schemaJson.getJSONObject(key);
                if (ts.optBoolean("is_view", false)) continue; // View DDL comes later

                writer.write("--\n-- Table structure for table " + key + "\n--\n");
                writer.write("DROP TABLE IF EXISTS " + key + ";\n");
                if (ts.has("definition")) {
                    String def = ts.getString("definition").trim();
                    if (!def.endsWith(";")) {
                        def += ";";
                    }
                    writer.write(def + "\n\n");
                } else {
                    writer.write(generateCreateTableSql(key, ts) + "\n\n");
                }

                // Table Rows / DML
                JSONArray rows = storage.readTableRows(dbName, key);
                if (rows.length() > 0) {
                    writer.write("--\n-- Dumping data for table " + key + "\n--\n");
                    JSONArray cols = ts.getJSONArray("columns");
                    JSONArray typs = ts.getJSONArray("types");

                    // Build column name string
                    StringBuilder colSb = new StringBuilder();
                    for (int i = 0; i < cols.length(); i++) {
                        if (i > 0) colSb.append(", ");
                        colSb.append(cols.getString(i));
                    }

                    // Bulk insert in batches of 500
                    int batchSize = 500;
                    for (int i = 0; i < rows.length(); i += batchSize) {
                        writer.write("INSERT INTO " + key + " (" + colSb + ") VALUES\n");
                        int end = Math.min(i + batchSize, rows.length());
                        for (int j = i; j < end; j++) {
                            JSONObject row = rows.getJSONObject(j);
                            StringBuilder valSb = new StringBuilder();
                            valSb.append("(");
                            for (int k = 0; k < cols.length(); k++) {
                                String colName = cols.getString(k);
                                String colType = typs.getString(k);
                                if (k > 0) valSb.append(", ");
                                if (row.isNull(colName)) {
                                    valSb.append("NULL");
                                } else {
                                    Object val = row.get(colName);
                                    valSb.append(formatSqlValue(val, colType));
                                }
                            }
                            valSb.append(")");
                            if (j == end - 1) {
                                valSb.append(";\n");
                            } else {
                                valSb.append(",\n");
                            }
                            writer.write(valSb.toString());
                        }
                    }
                    writer.write("\n");
                }
            }

            // 2. Export Views
            keys = schemaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("__") && key.endsWith("__")) continue;
                JSONObject ts = schemaJson.getJSONObject(key);
                if (ts.optBoolean("is_view", false)) {
                    writer.write("--\n-- Structure for view " + key + "\n--\n");
                    writer.write("DROP VIEW IF EXISTS " + key + ";\n");
                    if (ts.has("definition")) {
                        String def = ts.getString("definition").trim();
                        if (!def.endsWith(";")) {
                            def += ";";
                        }
                        writer.write(def + "\n\n");
                    } else {
                        writer.write("CREATE VIEW " + key + " AS " + ts.getString("query") + ";\n\n");
                    }
                }
            }

            // 3. Export Procedures
            JSONObject procs = schemaJson.optJSONObject("__procedures__");
            if (procs != null) {
                Iterator<String> pKeys = procs.keys();
                while (pKeys.hasNext()) {
                    String pKey = pKeys.next();
                    JSONObject proc = procs.getJSONObject(pKey);
                    writer.write("--\n-- Procedure " + pKey + "\n--\n");
                    writer.write("DROP PROCEDURE IF EXISTS " + pKey + ";\n");
                    writer.write("DELIMITER //\n");
                    writer.write(proc.getString("definition").trim() + " //\n");
                    writer.write("DELIMITER ;\n\n");
                }
            }

            // 4. Export Functions
            JSONObject fns = schemaJson.optJSONObject("__functions__");
            if (fns != null) {
                Iterator<String> fKeys = fns.keys();
                while (fKeys.hasNext()) {
                    String fKey = fKeys.next();
                    JSONObject fn = fns.getJSONObject(fKey);
                    writer.write("--\n-- Function " + fKey + "\n--\n");
                    writer.write("DROP FUNCTION IF EXISTS " + fKey + ";\n");
                    writer.write("DELIMITER //\n");
                    
                    String defStr;
                    if (fn.has("definition")) {
                        defStr = fn.getString("definition");
                    } else {
                        StringBuilder fnSb = new StringBuilder();
                        fnSb.append("CREATE FUNCTION ").append(fKey).append("(");
                        JSONArray params = fn.optJSONArray("parameters");
                        if (params != null) {
                            for (int i = 0; i < params.length(); i++) {
                                if (i > 0) fnSb.append(", ");
                                JSONObject param = params.optJSONObject(i);
                                fnSb.append(param.optString("name")).append(" ").append(param.optString("type"));
                            }
                        }
                        fnSb.append(") RETURNS ").append(fn.optString("returnType")).append("\nBEGIN\n    ");
                        
                        JSONArray bodyArr = fn.optJSONArray("body");
                        if (bodyArr != null) {
                            for (int i = 0; i < bodyArr.length(); i++) {
                                JSONObject tok = bodyArr.optJSONObject(i);
                                if (tok != null) {
                                    fnSb.append(tok.optString("value", "")).append(" ");
                                }
                            }
                        }
                        fnSb.append("\nEND");
                        defStr = fnSb.toString();
                    }
                    
                    writer.write(defStr.trim() + " //\n");
                    writer.write("DELIMITER ;\n\n");
                }
            }

            // 5. Export Triggers
            JSONObject triggers = schemaJson.optJSONObject("__triggers__");
            if (triggers != null) {
                Iterator<String> tKeys = triggers.keys();
                while (tKeys.hasNext()) {
                    String tKey = tKeys.next();
                    JSONObject trigger = triggers.getJSONObject(tKey);
                    writer.write("--\n-- Trigger " + tKey + "\n--\n");
                    writer.write("DROP TRIGGER IF EXISTS " + tKey + ";\n");
                    writer.write("DELIMITER //\n");
                    writer.write(trigger.getString("definition").trim() + " //\n");
                    writer.write("DELIMITER ;\n\n");
                }
            }

            // 6. Export Events
            JSONObject events = schemaJson.optJSONObject("__events__");
            if (events != null) {
                Iterator<String> eKeys = events.keys();
                while (eKeys.hasNext()) {
                    String eKey = eKeys.next();
                    JSONObject event = events.getJSONObject(eKey);
                    writer.write("--\n-- Event " + eKey + "\n--\n");
                    writer.write("DROP EVENT IF EXISTS " + eKey + ";\n");
                    writer.write("DELIMITER //\n");
                    writer.write(event.getString("definition").trim() + " //\n");
                    writer.write("DELIMITER ;\n\n");
                }
            }

            writer.write("SET FOREIGN_KEY_CHECKS = 1;\n");
            writer.flush();
        }
    }

    private static String generateCreateTableSql(String tableName, JSONObject tableSchema) throws Exception {
        return generateCreateTableSql(tableName, tableSchema, false);
    }

    private static String generateCreateTableSql(String tableName, JSONObject tableSchema, boolean isSQLite) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append(" (\n");

        JSONArray cols = tableSchema.getJSONArray("columns");
        JSONArray typs = tableSchema.getJSONArray("types");
        JSONObject defaults = tableSchema.optJSONObject("defaults");
        JSONObject nullables = tableSchema.optJSONObject("nullables");
        JSONObject extras = tableSchema.optJSONObject("extras");
        JSONObject fks = tableSchema.optJSONObject("foreign_keys");
        JSONArray primaryKey = tableSchema.optJSONArray("primary_key");
        JSONArray uniques = tableSchema.optJSONArray("uniques");
        JSONArray checks = tableSchema.optJSONArray("checks");

        List<String> colLines = new ArrayList<>();
        
        // Find if we have a single primary key column that is auto-incrementing (for SQLite column-level AUTOINCREMENT constraint)
        String autoIncPkCol = null;
        if (isSQLite && primaryKey != null && primaryKey.length() == 1) {
            String pkCol = primaryKey.getString(0);
            if (extras != null && extras.has(pkCol) && "auto_increment".equalsIgnoreCase(extras.getString(pkCol))) {
                autoIncPkCol = pkCol;
            }
        }

        for (int i = 0; i < cols.length(); i++) {
            String colName = cols.getString(i);
            String colType = typs.getString(i);
            StringBuilder colSb = new StringBuilder();
            
            if (colName.equals(autoIncPkCol)) {
                colSb.append("  ").append(colName).append(" INTEGER PRIMARY KEY AUTOINCREMENT");
            } else {
                colSb.append("  ").append(colName).append(" ").append(colType);

                // Nullability
                if (nullables != null && !nullables.optBoolean(colName, true)) {
                    colSb.append(" NOT NULL");
                }

                // Default
                if (defaults != null && defaults.has(colName) && !defaults.isNull(colName)) {
                    String defVal = defaults.get(colName).toString();
                    String formattedDefVal = formatSqlDefaultValue(defVal, colType);
                    if (isSQLite && ("now()".equalsIgnoreCase(formattedDefVal) || "current_timestamp()".equalsIgnoreCase(formattedDefVal))) {
                        formattedDefVal = "CURRENT_TIMESTAMP";
                    }
                    colSb.append(" DEFAULT ").append(formattedDefVal);
                }

                // Extra
                if (extras != null && extras.has(colName) && !extras.isNull(colName)) {
                    String extraVal = extras.getString(colName);
                    if (!isSQLite || !"auto_increment".equalsIgnoreCase(extraVal)) {
                        colSb.append(" ").append(extraVal.toUpperCase());
                    }
                }
            }

            colLines.add(colSb.toString());
        }

        // Primary key
        if (primaryKey != null && primaryKey.length() > 0) {
            if (autoIncPkCol == null) {
                StringBuilder pkSb = new StringBuilder();
                pkSb.append("  PRIMARY KEY (");
                for (int i = 0; i < primaryKey.length(); i++) {
                    if (i > 0) pkSb.append(",");
                    pkSb.append(primaryKey.getString(i));
                }
                pkSb.append(")");
                colLines.add(pkSb.toString());
            }
        }

        // Uniques
        if (uniques != null) {
            for (int i = 0; i < uniques.length(); i++) {
                JSONArray uniqueGroup = uniques.getJSONArray(i);
                StringBuilder uqSb = new StringBuilder();
                if (isSQLite) {
                    uqSb.append("  UNIQUE (");
                } else {
                    uqSb.append("  UNIQUE KEY uq_");
                    for (int j = 0; j < uniqueGroup.length(); j++) {
                        if (j > 0) uqSb.append("_");
                        uqSb.append(uniqueGroup.getString(j));
                    }
                    uqSb.append(" (");
                }
                for (int j = 0; j < uniqueGroup.length(); j++) {
                    if (j > 0) uqSb.append(",");
                    uqSb.append(uniqueGroup.getString(j));
                }
                uqSb.append(")");
                colLines.add(uqSb.toString());
            }
        }

        // Checks
        if (checks != null) {
            for (int i = 0; i < checks.length(); i++) {
                JSONObject check = checks.getJSONObject(i);
                String expr = getCheckExpression(check);
                if (!expr.isEmpty()) {
                    colLines.add("  CHECK (" + expr + ")");
                }
            }
        }

        // Foreign keys
        if (fks != null) {
            Iterator<String> fkKeys = fks.keys();
            while (fkKeys.hasNext()) {
                String col = fkKeys.next();
                if (fks.isNull(col)) continue;
                String ref = fks.getString(col);
                int dotIdx = ref.indexOf('.');
                if (dotIdx > 0) {
                    String refTable = ref.substring(0, dotIdx);
                    String refCol = ref.substring(dotIdx + 1);
                    colLines.add("  FOREIGN KEY (" + col + ") REFERENCES " + refTable + "(" + refCol + ")");
                }
            }
        }

        sb.append(String.join(",\n", colLines));
        sb.append("\n)");

        if (!isSQLite) {
            String charset = tableSchema.optString("charset");
            String collation = tableSchema.optString("collation");
            if (!charset.isEmpty()) {
                sb.append(" DEFAULT CHARACTER SET = ").append(charset);
            }
            if (!collation.isEmpty()) {
                sb.append(" COLLATE = ").append(collation);
            }
        }
        sb.append(";");
        return sb.toString();
    }

    private static String getCheckExpression(JSONObject check) {
        String col = check.optString("column");
        String op = check.optString("operator").toUpperCase();
        if ("BETWEEN".equals(op)) {
            JSONArray range = check.optJSONArray("values");
            if (range != null && range.length() >= 2) {
                return col + " BETWEEN " + formatCheckValue(range.opt(0)) + " AND " + formatCheckValue(range.opt(1));
            }
        } else if ("IN".equals(op)) {
            JSONArray allowed = check.optJSONArray("values");
            if (allowed != null) {
                List<String> formatted = new ArrayList<>();
                for (int i = 0; i < allowed.length(); i++) {
                    formatted.add(formatCheckValue(allowed.opt(i)));
                }
                return col + " IN (" + String.join(", ", formatted) + ")";
            }
        } else {
            Object val = check.opt("value");
            return col + " " + op + " " + formatCheckValue(val);
        }
        return "";
    }

    private static String formatCheckValue(Object val) {
        if (val == null || val == JSONObject.NULL) return "NULL";
        if (val instanceof Number || val instanceof Boolean) {
            return val.toString();
        }
        return "'" + val.toString().replace("'", "''") + "'";
    }

    private static String formatSqlValue(Object val, String type) {
        if (val == null) return "NULL";
        String typUpper = type.toUpperCase();
        if (typUpper.contains("INT") || typUpper.contains("DOUBLE") || typUpper.contains("DECIMAL") || typUpper.contains("FLOAT") || typUpper.contains("NUMERIC")) {
            return val.toString();
        }
        return "'" + val.toString().replace("'", "''") + "'";
    }

    private static String formatSqlDefaultValue(String val, String type) {
        if (val.equalsIgnoreCase("null")) return "NULL";
        if (val.equalsIgnoreCase("current_timestamp") || val.equalsIgnoreCase("now()")) return val;
        String typUpper = type.toUpperCase();
        if (typUpper.contains("INT") || typUpper.contains("DOUBLE") || typUpper.contains("DECIMAL") || typUpper.contains("FLOAT") || typUpper.contains("NUMERIC")) {
            return val;
        }
        // If it starts with quotes already, return it
        if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith("\"") && val.endsWith("\""))) {
            return val;
        }
        return "'" + val.replace("'", "''") + "'";
    }

    private static void importSql(DatabaseEngine engine, String dbName, InputStream is) throws Exception {
        SqlScriptRunner.runScript(engine, is, dbName);
    }

    // ── CSV EXPORT / IMPORT ─────────────────────────────────────────────────

    private static void exportCsvZip(DatabaseEngine engine, String dbName, OutputStream os) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        JSONObject schemaJson = storage.readSchema(dbName);

        try (ZipOutputStream zos = new ZipOutputStream(os)) {
            Iterator<String> keys = schemaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("__") && key.endsWith("__")) continue;
                JSONObject ts = schemaJson.getJSONObject(key);
                if (ts.optBoolean("is_view", false)) continue; // skip views in CSV output

                JSONArray cols = ts.getJSONArray("columns");
                JSONArray rows = storage.readTableRows(dbName, key);

                ZipEntry entry = new ZipEntry(key + ".csv");
                zos.putNextEntry(entry);

                // Construct CSV string
                StringBuilder sb = new StringBuilder();
                // Header
                for (int i = 0; i < cols.length(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(escapeCsvField(cols.getString(i)));
                }
                sb.append("\n");

                // Rows
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    for (int j = 0; j < cols.length(); j++) {
                        if (j > 0) sb.append(",");
                        String col = cols.getString(j);
                        if (!row.isNull(col)) {
                            sb.append(escapeCsvField(row.get(col).toString()));
                        }
                    }
                    sb.append("\n");
                }

                byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
                zos.write(data);
                zos.closeEntry();
            }
        }
    }

    private static void importCsvZip(DatabaseEngine engine, String dbName, InputStream is) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.toLowerCase().endsWith(".csv")) {
                    String tableName = name.substring(0, name.length() - 4);
                    // Filter unsafe names
                    tableName = tableName.replaceAll("[^a-zA-Z0-9_]", "");
                    if (tableName.isEmpty()) continue;

                    // Read content
                    byte[] data = readAllBytes(zis);
                    importSingleCsv(engine, dbName, tableName, new ByteArrayInputStream(data));
                }
                zis.closeEntry();
            }
        }
    }

    private static void importSingleCsv(DatabaseEngine engine, String dbName, String tableName, InputStream is) throws Exception {
        String content = new String(readAllBytes(is), StandardCharsets.UTF_8);
        List<List<String>> csvRows = parseCsv(content);
        importTableData(engine, dbName, tableName, csvRows);
    }

    private static void importTableData(DatabaseEngine engine, String dbName, String tableName, List<List<String>> dataRows) throws Exception {
        if (dataRows.isEmpty()) return;

        List<String> headers = dataRows.get(0);
        List<List<String>> bodyRows = dataRows.subList(1, dataRows.size());

        // Infer schemas
        List<String> types = new ArrayList<>();
        for (int col = 0; col < headers.size(); col++) {
            types.add(inferColumnType(bodyRows, col));
        }

        // Clean headers to valid column names
        List<String> cleanedHeaders = new ArrayList<>();
        for (String h : headers) {
            String clean = h.trim().replaceAll("[^a-zA-Z0-9_]", "");
            if (clean.isEmpty()) {
                clean = "col_" + cleanedHeaders.size();
            }
            cleanedHeaders.add(clean);
        }

        // Ensure database exists and is selected
        engine.execute("CREATE DATABASE IF NOT EXISTS " + dbName + ";");
        engine.execute("USE " + dbName + ";");

        // DROP table if exists first to make it a fresh replace import
        engine.execute("DROP TABLE IF EXISTS " + tableName + ";");

        // Build CREATE TABLE
        StringBuilder createSb = new StringBuilder();
        createSb.append("CREATE TABLE ").append(tableName).append(" (");
        for (int i = 0; i < cleanedHeaders.size(); i++) {
            if (i > 0) createSb.append(", ");
            createSb.append(cleanedHeaders.get(i)).append(" ").append(types.get(i));
        }
        createSb.append(");");
        QueryResult res = engine.execute(createSb.toString());
        if (!res.success) {
            throw new Exception("Failed to create table '" + tableName + "' on import: " + res.message);
        }

        // Insert rows inside a single transaction for speed
        if (!bodyRows.isEmpty()) {
            engine.execute("START TRANSACTION;");
            try {
                for (List<String> row : bodyRows) {
                    StringBuilder insertSb = new StringBuilder();
                    insertSb.append("INSERT INTO ").append(tableName).append(" (");
                    for (int i = 0; i < cleanedHeaders.size(); i++) {
                        if (i > 0) insertSb.append(", ");
                        insertSb.append(cleanedHeaders.get(i));
                    }
                    insertSb.append(") VALUES (");
                    for (int i = 0; i < cleanedHeaders.size(); i++) {
                        if (i > 0) insertSb.append(", ");
                        if (i >= row.size()) {
                            insertSb.append("NULL");
                        } else {
                            String val = row.get(i);
                            if (val == null || val.trim().isEmpty() || val.equalsIgnoreCase("null")) {
                                insertSb.append("NULL");
                            } else {
                                String type = types.get(i);
                                if ("INT".equals(type) || "DOUBLE".equals(type)) {
                                    insertSb.append(val);
                                } else {
                                    insertSb.append("'").append(val.replace("'", "''")).append("'");
                                }
                            }
                        }
                    }
                    insertSb.append(");");
                    engine.execute(insertSb.toString());
                }
                engine.execute("COMMIT;");
            } catch (Exception e) {
                engine.execute("ROLLBACK;");
                throw e;
            }
        }
    }

    private static String inferColumnType(List<List<String>> dataRows, int colIndex) {
        boolean allInt = true;
        boolean allDouble = true;
        boolean hasData = false;

        for (List<String> row : dataRows) {
            if (colIndex >= row.size()) continue;
            String val = row.get(colIndex);
            if (val == null || val.trim().isEmpty() || val.equalsIgnoreCase("null")) {
                continue;
            }
            hasData = true;
            String t = val.trim();

            if (allInt) {
                if (!t.matches("-?\\d+")) {
                    allInt = false;
                }
            }
            if (allDouble) {
                if (!t.matches("-?\\d+(\\.\\d+)?") && !t.matches("-?\\.\\d+")) {
                    allDouble = false;
                }
            }
        }

        if (!hasData) {
            return "TEXT";
        }
        if (allInt) {
            return "INT";
        }
        if (allDouble) {
            return "DOUBLE";
        }
        return "TEXT";
    }

    public static List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int len = content.length();
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++; // skip next quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(cell.toString());
                    cell.setLength(0);
                } else if (c == '\r' || c == '\n') {
                    currentRow.add(cell.toString());
                    cell.setLength(0);
                    if (!currentRow.isEmpty() || (currentRow.size() == 1 && !currentRow.get(0).isEmpty())) {
                        rows.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                    if (c == '\r' && i + 1 < len && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                } else {
                    cell.append(c);
                }
            }
        }
        if (cell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(cell.toString());
            rows.add(currentRow);
        }
        return rows;
    }

    private static String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    // ── OPENXML XLSX EXPORT ───────────────────────────────────────────

    private static class NoCloseOutputStream extends OutputStream {
        private final OutputStream out;
        public NoCloseOutputStream(OutputStream out) {
            this.out = out;
        }
        @Override
        public void write(int b) throws java.io.IOException {
            out.write(b);
        }
        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            out.write(b, off, len);
        }
        @Override
        public void flush() throws java.io.IOException {
            out.flush();
        }
        @Override
        public void close() {
            // Do not close the underlying stream
        }
    }

    private static String getColName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col >= 0) {
            sb.insert(0, (char) ('A' + (col % 26)));
            col = (col / 26) - 1;
        }
        return sb.toString();
    }

    private static int getColIndex(String ref) {
        int index = 0;
        int col = 0;
        while (index < ref.length() && Character.isLetter(ref.charAt(index))) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(index)) - 'A' + 1);
            index++;
        }
        return col - 1;
    }

    private static void exportXlsx(DatabaseEngine engine, String dbName, OutputStream os) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        JSONObject schemaJson = storage.readSchema(dbName);
        List<String> tableNames = new ArrayList<>();
        Iterator<String> keys = schemaJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith("__") && key.endsWith("__")) continue;
            JSONObject ts = schemaJson.getJSONObject(key);
            if (ts.optBoolean("is_view", false)) continue;
            tableNames.add(key);
        }
        if (tableNames.isEmpty()) {
            tableNames.add("Sheet1");
        }

        try (ZipOutputStream zos = new ZipOutputStream(os)) {
            // 1. [Content_Types].xml
            ZipEntry ctEntry = new ZipEntry("[Content_Types].xml");
            zos.putNextEntry(ctEntry);
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n");
            sb.append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n");
            sb.append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");
            sb.append("  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n");
            sb.append("  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n");
            for (int i = 0; i < tableNames.size(); i++) {
                sb.append("  <Override PartName=\"/xl/worksheets/sheet").append(i + 1).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
            }
            sb.append("</Types>\n");
            zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. _rels/.rels
            ZipEntry relsEntry = new ZipEntry("_rels/.rels");
            zos.putNextEntry(relsEntry);
            String relsStr = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                "</Relationships>\n";
            zos.write(relsStr.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. xl/styles.xml
            ZipEntry stylesEntry = new ZipEntry("xl/styles.xml");
            zos.putNextEntry(stylesEntry);
            String stylesStr = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>\n" +
                "  <fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>\n" +
                "  <borders count=\"1\"><border><left/><right/><top/><bottom/></border></borders>\n" +
                "  <cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>\n" +
                "  <cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>\n" +
                "</styleSheet>\n";
            zos.write(stylesStr.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. xl/workbook.xml
            ZipEntry wbEntry = new ZipEntry("xl/workbook.xml");
            zos.putNextEntry(wbEntry);
            sb.setLength(0);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
            sb.append("  <sheets>\n");
            for (int i = 0; i < tableNames.size(); i++) {
                sb.append("    <sheet name=\"").append(escapeXml(tableNames.get(i))).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>\n");
            }
            sb.append("  </sheets>\n");
            sb.append("</workbook>\n");
            zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 5. xl/_rels/workbook.xml.rels
            ZipEntry wbRelsEntry = new ZipEntry("xl/_rels/workbook.xml.rels");
            zos.putNextEntry(wbRelsEntry);
            sb.setLength(0);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
            for (int i = 0; i < tableNames.size(); i++) {
                sb.append("  <Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>\n");
            }
            sb.append("  <Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n");
            sb.append("</Relationships>\n");
            zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 6. xl/worksheets/sheet{i}.xml
            NoCloseOutputStream ncos = new NoCloseOutputStream(zos);
            for (int i = 0; i < tableNames.size(); i++) {
                String tableName = tableNames.get(i);
                ZipEntry sheetEntry = new ZipEntry("xl/worksheets/sheet" + (i + 1) + ".xml");
                zos.putNextEntry(sheetEntry);

                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ncos, StandardCharsets.UTF_8));
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
                writer.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
                writer.write("  <sheetData>\n");

                JSONObject ts = schemaJson.optJSONObject(tableName);
                if (ts != null) {
                    JSONArray cols = ts.getJSONArray("columns");
                    JSONArray typs = ts.getJSONArray("types");
                    JSONArray rows = storage.readTableRows(dbName, tableName);

                    // Header row (row index 1)
                    writer.write("    <row r=\"1\">\n");
                    for (int colIdx = 0; colIdx < cols.length(); colIdx++) {
                        String colName = cols.getString(colIdx);
                        String ref = getColName(colIdx) + "1";
                        writer.write("      <c r=\"" + ref + "\" t=\"inlineStr\"><is><t>" + escapeXml(colName) + "</t></is></c>\n");
                    }
                    writer.write("    </row>\n");

                    // Data rows (row index 2 to rows.length() + 1)
                    for (int rowIdx = 0; rowIdx < rows.length(); rowIdx++) {
                        JSONObject row = rows.getJSONObject(rowIdx);
                        int excelRowIdx = rowIdx + 2;
                        writer.write("    <row r=\"" + excelRowIdx + "\">\n");
                        for (int colIdx = 0; colIdx < cols.length(); colIdx++) {
                            String col = cols.getString(colIdx);
                            String colType = typs.getString(colIdx).toUpperCase();
                            String ref = getColName(colIdx) + excelRowIdx;

                            if (row.isNull(col)) {
                                // Skip empty cells
                            } else {
                                Object val = row.get(col);
                                if (colType.contains("INT") || colType.contains("DOUBLE") || colType.contains("DECIMAL") || colType.contains("FLOAT")) {
                                    writer.write("      <c r=\"" + ref + "\" t=\"n\"><v>" + val.toString() + "</v></c>\n");
                                } else if (val instanceof Boolean) {
                                    writer.write("      <c r=\"" + ref + "\" t=\"b\"><v>" + ((Boolean) val ? "1" : "0") + "</v></c>\n");
                                } else {
                                    writer.write("      <c r=\"" + ref + "\" t=\"inlineStr\"><is><t>" + escapeXml(val.toString()) + "</t></is></c>\n");
                                }
                            }
                        }
                        writer.write("    </row>\n");
                    }
                } else {
                    writer.write("    <row r=\"1\">\n");
                    writer.write("      <c r=\"A1\" t=\"inlineStr\"><is><t>Empty Sheet</t></is></c>\n");
                    writer.write("    </row>\n");
                }

                writer.write("  </sheetData>\n");
                writer.write("</worksheet>\n");
                writer.flush();
                zos.closeEntry();
            }
        }
    }

    private static void importSpreadsheetML(DatabaseEngine engine, String dbName, InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);

        NodeList worksheets = doc.getElementsByTagName("Worksheet");
        for (int i = 0; i < worksheets.getLength(); i++) {
            Element worksheet = (Element) worksheets.item(i);
            String tableName = worksheet.getAttribute("ss:Name");
            if (tableName.isEmpty()) {
                tableName = worksheet.getAttribute("Name");
            }
            tableName = tableName.replaceAll("[^a-zA-Z0-9_]", "");
            if (tableName.isEmpty()) {
                tableName = "imported_sheet_" + i;
            }

            NodeList rowsList = worksheet.getElementsByTagName("Row");
            List<List<String>> dataRows = new ArrayList<>();
            for (int j = 0; j < rowsList.getLength(); j++) {
                Element rowEl = (Element) rowsList.item(j);
                NodeList cellList = rowEl.getElementsByTagName("Cell");
                List<String> cellValues = new ArrayList<>();
                for (int k = 0; k < cellList.getLength(); k++) {
                    Element cellEl = (Element) cellList.item(k);
                    NodeList dataList = cellEl.getElementsByTagName("Data");
                    String val = "";
                    if (dataList.getLength() > 0) {
                        val = dataList.item(0).getTextContent();
                    }
                    cellValues.add(val);
                }
                dataRows.add(cellValues);
            }

            importTableData(engine, dbName, tableName, dataRows);
        }
    }

    private static void importXlsx(DatabaseEngine engine, String dbName, byte[] zipData) throws Exception {
        // 1. Parse shared strings
        List<String> sharedStrings = parseSharedStrings(zipData);
        // 2. Parse workbook relationships
        Map<String, String> rels = parseWorkbookRels(zipData);
        // 3. Parse workbook sheets
        Map<String, String> sheetPaths = parseWorkbookSheets(zipData, rels);

        // Fallback: if sheets mapping is empty, scan zip for worksheets
        if (sheetPaths.isEmpty()) {
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                int count = 1;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.toLowerCase().startsWith("xl/worksheets/sheet") && name.toLowerCase().endsWith(".xml")) {
                        sheetPaths.put("Sheet" + count, name);
                        count++;
                    }
                    zis.closeEntry();
                }
            }
        }

        // For each sheet, import data as a table
        for (Map.Entry<String, String> entry : sheetPaths.entrySet()) {
            String sheetName = entry.getKey();
            String sheetPath = entry.getValue();

            List<List<String>> dataRows = parseWorksheet(zipData, sheetPath, sharedStrings);
            if (dataRows.isEmpty()) continue;

            String tableName = sheetName.replaceAll("[^a-zA-Z0-9_]", "");
            if (tableName.isEmpty()) {
                tableName = "imported_sheet";
            }

            importTableData(engine, dbName, tableName, dataRows);
        }
    }

    private static List<String> parseSharedStrings(byte[] zipData) throws Exception {
        List<String> sharedStrings = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/sharedStrings.xml".equalsIgnoreCase(entry.getName())) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(zis);
                    NodeList siList = doc.getElementsByTagName("si");
                    for (int i = 0; i < siList.getLength(); i++) {
                        sharedStrings.add(siList.item(i).getTextContent());
                    }
                    break;
                }
                zis.closeEntry();
            }
        }
        return sharedStrings;
    }

    private static Map<String, String> parseWorkbookRels(byte[] zipData) throws Exception {
        Map<String, String> rels = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/_rels/workbook.xml.rels".equalsIgnoreCase(entry.getName())) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(zis);
                    NodeList relList = doc.getElementsByTagName("Relationship");
                    for (int i = 0; i < relList.getLength(); i++) {
                        Element el = (Element) relList.item(i);
                        String id = el.getAttribute("Id");
                        String target = el.getAttribute("Target");
                        rels.put(id, target);
                    }
                    break;
                }
                zis.closeEntry();
            }
        }
        return rels;
    }

    private static Map<String, String> parseWorkbookSheets(byte[] zipData, Map<String, String> rels) throws Exception {
        Map<String, String> sheetPaths = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/workbook.xml".equalsIgnoreCase(entry.getName())) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(zis);
                    NodeList sheetList = doc.getElementsByTagName("sheet");
                    for (int i = 0; i < sheetList.getLength(); i++) {
                        Element el = (Element) sheetList.item(i);
                        String name = el.getAttribute("name");
                        String rId = el.getAttribute("r:id");
                        if (rId.isEmpty()) {
                            rId = el.getAttribute("id");
                        }
                        String target = rels.get(rId);
                        if (target != null) {
                            String path = target;
                            if (!path.startsWith("xl/") && !path.startsWith("/")) {
                                path = "xl/" + path;
                            } else if (path.startsWith("/")) {
                                path = path.substring(1);
                            }
                            sheetPaths.put(name, path);
                        }
                    }
                    break;
                }
                zis.closeEntry();
            }
        }
        return sheetPaths;
    }

    private static List<List<String>> parseWorksheet(byte[] zipData, String sheetPath, List<String> sharedStrings) throws Exception {
        List<List<String>> dataRows = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (sheetPath.equalsIgnoreCase(entry.getName())) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(zis);

                    NodeList rowsList = doc.getElementsByTagName("row");
                    for (int j = 0; j < rowsList.getLength(); j++) {
                        Element rowEl = (Element) rowsList.item(j);
                        NodeList cellList = rowEl.getElementsByTagName("c");
                        List<String> cellValues = new ArrayList<>();
                        int lastColIdx = -1;
                        for (int k = 0; k < cellList.getLength(); k++) {
                            Element cellEl = (Element) cellList.item(k);
                            int colIdx = lastColIdx + 1;
                            String r = cellEl.getAttribute("r");
                            if (!r.isEmpty()) {
                                colIdx = getColIndex(r);
                            }
                            lastColIdx = colIdx;

                            while (cellValues.size() <= colIdx) {
                                cellValues.add("");
                            }

                            String val = "";
                            String t = cellEl.getAttribute("t");
                            if ("inlineStr".equals(t)) {
                                NodeList isList = cellEl.getElementsByTagName("is");
                                if (isList.getLength() > 0) {
                                    Element isEl = (Element) isList.item(0);
                                    NodeList tList = isEl.getElementsByTagName("t");
                                    if (tList.getLength() > 0) {
                                        val = tList.item(0).getTextContent();
                                    }
                                }
                            } else if ("s".equals(t)) {
                                NodeList vList = cellEl.getElementsByTagName("v");
                                if (vList.getLength() > 0) {
                                    try {
                                        int idx = Integer.parseInt(vList.item(0).getTextContent().trim());
                                        if (idx >= 0 && idx < sharedStrings.size()) {
                                            val = sharedStrings.get(idx);
                                        }
                                    } catch (Exception ignored) {}
                                }
                            } else if ("b".equals(t)) {
                                NodeList vList = cellEl.getElementsByTagName("v");
                                if (vList.getLength() > 0) {
                                    String vText = vList.item(0).getTextContent().trim();
                                    val = "1".equals(vText) || "true".equalsIgnoreCase(vText) ? "1" : "0";
                                }
                            } else {
                                NodeList vList = cellEl.getElementsByTagName("v");
                                if (vList.getLength() > 0) {
                                    val = vList.item(0).getTextContent().trim();
                                }
                            }
                            cellValues.set(colIdx, val);
                        }

                        if (!cellValues.isEmpty()) {
                            dataRows.add(cellValues);
                        }
                    }
                    break;
                }
                zis.closeEntry();
            }
        }
        return dataRows;
    }

    // ── COMMON HELPERS ──────────────────────────────────────────────────────

    private static String escapeXml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    public static boolean isAndroid() {
        String vendor = System.getProperty("java.vendor");
        return vendor != null && vendor.toLowerCase().contains("android");
    }

    private static void exportSQLite(DatabaseEngine engine, String dbName, OutputStream os) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        File baseDir = storage.getDatabasesDir().getParentFile();
        File tempFile = File.createTempFile("pocketsql_", ".db", baseDir);
        try {
            android.database.sqlite.SQLiteDatabase sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(tempFile, null);
            
            JSONObject schemaJson = storage.readSchema(dbName);
            
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS __pocketsql_metadata__ (key TEXT PRIMARY KEY, value TEXT);");
            
            android.database.sqlite.SQLiteStatement stmt = sqliteDb.compileStatement(
                "INSERT OR REPLACE INTO __pocketsql_metadata__ (key, value) VALUES (?, ?);"
            );
            stmt.bindString(1, "schema");
            stmt.bindString(2, schemaJson.toString());
            stmt.executeInsert();
            stmt.close();
            
            Iterator<String> keys = schemaJson.keys();
            while (keys.hasNext()) {
                String tableName = keys.next();
                if (tableName.startsWith("__") && tableName.endsWith("__")) continue;
                
                JSONObject ts = schemaJson.getJSONObject(tableName);
                if (ts.optBoolean("is_view", false)) continue;
                
                String createSql = generateCreateTableSql(tableName, ts, true);
                sqliteDb.execSQL(createSql);
                
                JSONArray rows = storage.readTableRows(dbName, tableName);
                if (rows.length() > 0) {
                    JSONArray cols = ts.getJSONArray("columns");
                    sqliteDb.beginTransaction();
                    try {
                        for (int i = 0; i < rows.length(); i++) {
                            JSONObject row = rows.getJSONObject(i);
                            android.content.ContentValues cv = new android.content.ContentValues();
                            for (int j = 0; j < cols.length(); j++) {
                                String colName = cols.getString(j);
                                if (row.isNull(colName)) {
                                    cv.putNull(colName);
                                } else {
                                    Object val = row.get(colName);
                                    if (val instanceof Integer || val instanceof Long) {
                                        cv.put(colName, ((Number) val).longValue());
                                    } else if (val instanceof Double || val instanceof Float) {
                                        cv.put(colName, ((Number) val).doubleValue());
                                    } else if (val instanceof Boolean) {
                                        cv.put(colName, (Boolean) val ? 1 : 0);
                                    } else {
                                        cv.put(colName, val.toString());
                                    }
                                }
                            }
                            sqliteDb.insert(tableName, null, cv);
                        }
                        sqliteDb.setTransactionSuccessful();
                    } finally {
                        sqliteDb.endTransaction();
                    }
                }
            }
            
            keys = schemaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("__") && key.endsWith("__")) continue;
                JSONObject ts = schemaJson.getJSONObject(key);
                if (ts.optBoolean("is_view", false)) {
                    try {
                        sqliteDb.execSQL("CREATE VIEW " + key + " AS " + ts.getString("query") + ";");
                    } catch (Exception ignored) {
                    }
                }
            }
            
            sqliteDb.close();
            
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static void importSQLite(DatabaseEngine engine, String dbName, InputStream is) throws Exception {
        StorageEngine storage = engine.getStorageEngine();
        File baseDir = storage.getDatabasesDir().getParentFile();
        File tempFile = File.createTempFile("pocketsql_import_", ".db", baseDir);
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        
        try {
            android.database.sqlite.SQLiteDatabase sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                tempFile.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            );
            
            JSONObject schemaJson = null;
            
            boolean metadataExists = false;
            try (android.database.Cursor cursor = sqliteDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='__pocketsql_metadata__';", null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    metadataExists = true;
                }
            }
            
            if (metadataExists) {
                try (android.database.Cursor cursor = sqliteDb.rawQuery(
                    "SELECT value FROM __pocketsql_metadata__ WHERE key='schema';", null
                )) {
                    if (cursor != null && cursor.moveToFirst()) {
                        schemaJson = new JSONObject(cursor.getString(0));
                    }
                }
            }
            
            if (schemaJson == null) {
                schemaJson = new JSONObject();
                
                try (android.database.Cursor tablesCursor = sqliteDb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != '__pocketsql_metadata__';", null
                )) {
                    if (tablesCursor != null) {
                        while (tablesCursor.moveToNext()) {
                            String tableName = tablesCursor.getString(0);
                            JSONObject tableSchema = new JSONObject();
                            tableSchema.put("columns", new JSONArray());
                            tableSchema.put("types", new JSONArray());
                            
                            try (android.database.Cursor infoCursor = sqliteDb.rawQuery(
                                "PRAGMA table_info(" + tableName + ");", null
                            )) {
                                if (infoCursor != null) {
                                    JSONArray cols = new JSONArray();
                                    JSONArray typs = new JSONArray();
                                    JSONObject nullables = new JSONObject();
                                    JSONObject defaults = new JSONObject();
                                    JSONArray primaryKey = new JSONArray();
                                    
                                    while (infoCursor.moveToNext()) {
                                        String colName = infoCursor.getString(1);
                                        String colType = infoCursor.getString(2);
                                        boolean notNull = infoCursor.getInt(3) == 1;
                                        String dflt = infoCursor.getString(4);
                                        boolean pk = infoCursor.getInt(5) > 0;
                                        
                                        cols.put(colName);
                                        String resolvedType = "TEXT";
                                        String colTypeUpper = colType.toUpperCase();
                                        if (colTypeUpper.contains("INT")) resolvedType = "INT";
                                        else if (colTypeUpper.contains("DOUBLE") || colTypeUpper.contains("REAL") || colTypeUpper.contains("FLOAT")) resolvedType = "DOUBLE";
                                        else if (colTypeUpper.contains("CHAR") || colTypeUpper.contains("TEXT") || colTypeUpper.contains("CLOB")) resolvedType = "VARCHAR(255)";
                                        
                                        typs.put(resolvedType);
                                        nullables.put(colName, !notNull);
                                        if (dflt != null) {
                                            defaults.put(colName, dflt);
                                        }
                                        if (pk) {
                                            primaryKey.put(colName);
                                        }
                                    }
                                    tableSchema.put("columns", cols);
                                    tableSchema.put("types", typs);
                                    tableSchema.put("nullables", nullables);
                                    if (defaults.length() > 0) {
                                        tableSchema.put("defaults", defaults);
                                    }
                                    if (primaryKey.length() > 0) {
                                        tableSchema.put("primary_key", primaryKey);
                                    }
                                }
                            }
                            schemaJson.put(tableName, tableSchema);
                        }
                    }
                }
                
                try (android.database.Cursor viewsCursor = sqliteDb.rawQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type='view';", null
                )) {
                    if (viewsCursor != null) {
                        while (viewsCursor.moveToNext()) {
                            String viewName = viewsCursor.getString(0);
                            String sql = viewsCursor.getString(1);
                            if (sql != null) {
                                int asIdx = sql.toUpperCase().indexOf(" AS ");
                                if (asIdx != -1) {
                                    String query = sql.substring(asIdx + 4).trim();
                                    JSONObject viewSchema = new JSONObject();
                                    viewSchema.put("is_view", true);
                                    viewSchema.put("query", query);
                                    schemaJson.put(viewName, viewSchema);
                                }
                            }
                        }
                    }
                }
            }
            
            storage.deleteDatabaseDir(dbName);
            storage.createDatabaseDir(dbName);
            
            Iterator<String> keys = schemaJson.keys();
            while (keys.hasNext()) {
                String tableName = keys.next();
                if (tableName.startsWith("__") && tableName.endsWith("__")) continue;
                
                JSONObject ts = schemaJson.getJSONObject(tableName);
                if (ts.optBoolean("is_view", false)) continue;
                
                JSONArray cols = ts.getJSONArray("columns");
                JSONArray rows = new JSONArray();
                
                try (android.database.Cursor cursor = sqliteDb.rawQuery("SELECT * FROM " + tableName + ";", null)) {
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            JSONObject rowObj = new JSONObject();
                            for (int i = 0; i < cols.length(); i++) {
                                String colName = cols.getString(i);
                                int colIdx = cursor.getColumnIndex(colName);
                                if (colIdx != -1) {
                                    if (cursor.isNull(colIdx)) {
                                        rowObj.put(colName, JSONObject.NULL);
                                    } else {
                                        int type = cursor.getType(colIdx);
                                        if (type == android.database.Cursor.FIELD_TYPE_INTEGER) {
                                            rowObj.put(colName, cursor.getLong(colIdx));
                                        } else if (type == android.database.Cursor.FIELD_TYPE_FLOAT) {
                                            rowObj.put(colName, cursor.getDouble(colIdx));
                                        } else {
                                            rowObj.put(colName, cursor.getString(colIdx));
                                        }
                                    }
                                }
                            }
                            rows.put(rowObj);
                        }
                    }
                }
                storage.writeTableRows(dbName, tableName, rows);
            }
            
            storage.writeSchema(dbName, schemaJson);
            
            sqliteDb.close();
            
            if (dbName.equalsIgnoreCase(engine.getActiveDatabase())) {
                engine.useDatabase(dbName);
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
