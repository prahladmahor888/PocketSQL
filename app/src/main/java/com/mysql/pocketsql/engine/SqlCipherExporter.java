package com.mysql.pocketsql.engine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteStatement;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class SqlCipherExporter {

    public static void exportSQLite(DatabaseEngine engine, String dbName, OutputStream os) throws Exception {
        Context context = SqlApiHelper.getContext();
        SqlCipherHelper.init(context);
        String pw = SqlCipherHelper.getOrGeneratePw(context);

        StorageEngine storage = engine.getStorageEngine();
        File baseDir = storage.getDatabasesDir().getParentFile();
        
        String randomName = AppIntegrityManager.decode(new int[]{90, 69, 73, 65, 79, 94, 89, 91, 70, 117}) + java.util.UUID.randomUUID().toString().replace("-", "") + AppIntegrityManager.decode(new int[]{4, 78, 72});
        File tempFile = new File(baseDir, randomName);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        
        try {
            SQLiteDatabase sqliteDb = SqlCipherHelper.openOrCreateDatabase(tempFile, pw);
            
            JSONObject schemaJson = storage.readSchema(dbName);
            
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS __pocketsql_metadata__ (key TEXT PRIMARY KEY, value TEXT);");
            
            SQLiteStatement stmt = sqliteDb.compileStatement(
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
                
                String createSql = DatabaseExporter.generateCreateTableSql(tableName, ts, true);
                sqliteDb.execSQL(createSql);
                
                TableData td = engine.getOrLoadTable(dbName + "." + tableName);
                JSONArray rows = td.toJSONArray();
                if (rows.length() > 0) {
                    JSONArray cols = ts.getJSONArray("columns");
                    sqliteDb.beginTransaction();
                    try {
                        for (int i = 0; i < rows.length(); i++) {
                            JSONObject row = rows.getJSONObject(i);
                            ContentValues cv = new ContentValues();
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
                        String createViewQuery = String.format("CREATE VIEW %s AS %s;", key, ts.getString("query"));
                        sqliteDb.execSQL(createViewQuery);
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

    public static void importSQLite(DatabaseEngine engine, String dbName, InputStream is) throws Exception {
        Context context = SqlApiHelper.getContext();
        SqlCipherHelper.init(context);
        String pw = SqlCipherHelper.getOrGeneratePw(context);

        StorageEngine storage = engine.getStorageEngine();
        File baseDir = storage.getDatabasesDir().getParentFile();
        
        String randomName = AppIntegrityManager.decode(new int[]{90, 69, 73, 65, 79, 94, 89, 91, 70, 117, 67, 71, 90, 69, 88, 94, 117}) + java.util.UUID.randomUUID().toString().replace("-", "") + AppIntegrityManager.decode(new int[]{4, 78, 72});
        File tempFile = new File(baseDir, randomName);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        
        try {
            SQLiteDatabase sqliteDb = SqlCipherHelper.openDatabase(tempFile.getAbsolutePath(), pw);
            
            JSONObject schemaJson = null;
            
            boolean metadataExists = false;
            try (Cursor cursor = sqliteDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='__pocketsql_metadata__';", null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    metadataExists = true;
                }
            }
            
            if (metadataExists) {
                try (Cursor cursor = sqliteDb.rawQuery(
                    "SELECT value FROM __pocketsql_metadata__ WHERE key='schema';", null
                )) {
                    if (cursor != null && cursor.moveToFirst()) {
                        schemaJson = new JSONObject(cursor.getString(0));
                    }
                }
            }
            
            if (schemaJson == null) {
                schemaJson = new JSONObject();
                
                try (Cursor tablesCursor = sqliteDb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != '__pocketsql_metadata__';", null
                )) {
                    if (tablesCursor != null) {
                        while (tablesCursor.moveToNext()) {
                            String tableName = tablesCursor.getString(0);
                            JSONObject tableSchema = new JSONObject();
                            tableSchema.put("columns", new JSONArray());
                            tableSchema.put("types", new JSONArray());
                            
                            String pragmaQuery = String.format("PRAGMA table_info(%s);", tableName);
                            try (Cursor infoCursor = sqliteDb.rawQuery(
                                pragmaQuery, null
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
                
                try (Cursor viewsCursor = sqliteDb.rawQuery(
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
                
                String selectQuery = String.format("SELECT * FROM %s;", tableName);
                try (Cursor cursor = sqliteDb.rawQuery(selectQuery, null)) {
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
                                        if (type == Cursor.FIELD_TYPE_INTEGER) {
                                            rowObj.put(colName, cursor.getLong(colIdx));
                                        } else if (type == Cursor.FIELD_TYPE_FLOAT) {
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

            if (dbName.equalsIgnoreCase("pocketsql")) {
                // Read the imported pocketsql tables and sync back to users.json!
                try {
                    JSONArray userRows = storage.readTableRows("pocketsql", "user");
                    JSONArray dbRows = storage.readTableRows("pocketsql", "db");
                    JSONObject syncedUsers = new JSONObject();
                    
                    for (int i = 0; i < userRows.length(); i++) {
                        JSONObject uRow = userRows.getJSONObject(i);
                        String user = uRow.optString("User");
                        String host = uRow.optString("Host");
                        if (user.isEmpty() || host.isEmpty()) continue;
                        
                        String key = user + "@" + host;
                        JSONObject userObj = new JSONObject();
                        userObj.put("password", uRow.optString("authentication_string"));
                        
                        JSONObject privileges = new JSONObject();
                        JSONArray globalPrivs = new JSONArray();
                        
                        if ("Y".equalsIgnoreCase(uRow.optString("Super_priv"))) {
                            globalPrivs.put("ALL");
                        } else {
                            if ("Y".equalsIgnoreCase(uRow.optString("Select_priv"))) globalPrivs.put("SELECT");
                            if ("Y".equalsIgnoreCase(uRow.optString("Insert_priv"))) globalPrivs.put("INSERT");
                            if ("Y".equalsIgnoreCase(uRow.optString("Update_priv"))) globalPrivs.put("UPDATE");
                            if ("Y".equalsIgnoreCase(uRow.optString("Delete_priv"))) globalPrivs.put("DELETE");
                            if ("Y".equalsIgnoreCase(uRow.optString("Create_priv"))) globalPrivs.put("CREATE");
                            if ("Y".equalsIgnoreCase(uRow.optString("Drop_priv"))) globalPrivs.put("DROP");
                            if ("Y".equalsIgnoreCase(uRow.optString("Grant_priv"))) globalPrivs.put("GRANT");
                            if ("Y".equalsIgnoreCase(uRow.optString("Index_priv"))) globalPrivs.put("INDEX");
                            if ("Y".equalsIgnoreCase(uRow.optString("Alter_priv"))) globalPrivs.put("ALTER");
                        }
                        
                        privileges.put("*.*", globalPrivs);
                        userObj.put("privileges", privileges);
                        syncedUsers.put(key, userObj);
                    }
                    
                    for (int i = 0; i < dbRows.length(); i++) {
                        JSONObject dRow = dbRows.getJSONObject(i);
                        String user = dRow.optString("User");
                        String host = dRow.optString("Host");
                        String db = dRow.optString("Db");
                        if (user.isEmpty() || host.isEmpty() || db.isEmpty()) continue;
                        
                        String key = user + "@" + host;
                        JSONObject userObj = syncedUsers.optJSONObject(key);
                        if (userObj == null) {
                            userObj = new JSONObject();
                            userObj.put("password", "");
                            userObj.put("privileges", new JSONObject());
                            syncedUsers.put(key, userObj);
                        }
                        
                        JSONObject privileges = userObj.optJSONObject("privileges");
                        if (privileges == null) {
                            privileges = new JSONObject();
                            userObj.put("privileges", privileges);
                        }
                        
                        JSONArray dbPrivs = new JSONArray();
                        if ("Y".equalsIgnoreCase(dRow.optString("Select_priv"))) dbPrivs.put("SELECT");
                        if ("Y".equalsIgnoreCase(dRow.optString("Insert_priv"))) dbPrivs.put("INSERT");
                        if ("Y".equalsIgnoreCase(dRow.optString("Update_priv"))) dbPrivs.put("UPDATE");
                        if ("Y".equalsIgnoreCase(dRow.optString("Delete_priv"))) dbPrivs.put("DELETE");
                        if ("Y".equalsIgnoreCase(dRow.optString("Create_priv"))) dbPrivs.put("CREATE");
                        if ("Y".equalsIgnoreCase(dRow.optString("Drop_priv"))) dbPrivs.put("DROP");
                        if ("Y".equalsIgnoreCase(dRow.optString("Grant_priv"))) dbPrivs.put("GRANT");
                        if ("Y".equalsIgnoreCase(dRow.optString("Index_priv"))) dbPrivs.put("INDEX");
                        if ("Y".equalsIgnoreCase(dRow.optString("Alter_priv"))) dbPrivs.put("ALTER");
                        
                        privileges.put(db + ".*", dbPrivs);
                    }
                    
                    if (syncedUsers.length() > 0) {
                        storage.writeUsers(syncedUsers);
                        engine.loadUsers();
                    }
                } catch (Exception e) {
                    SqlLog.printStackTrace(e);
                }
            }
            
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
