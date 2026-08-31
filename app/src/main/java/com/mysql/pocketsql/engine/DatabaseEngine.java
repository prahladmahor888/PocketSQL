package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseEngine {
    final StorageEngine storageEngine;
    String activeDatabaseName;
    
    // Schema cache: mapping tableName -> TableData definition
    final Map<String, TableData> tableCache = new HashMap<>();
    JSONObject activeSchemaJson;

    // Cache of table names: lowercase -> actual name
    private final Map<String, String> tableNameCache = new HashMap<>();
    private JSONObject lastResolvedSchema = null;

    // Authentication & Authorization state
    String currentUser = null;
    String currentHost = null;
    JSONObject cachedUsers = null;

    // Transaction state
    boolean inTransaction = false;
    File txBackupDir = null;
    final Map<String, File> savepoints = new HashMap<>();
    private boolean foreignKeyChecks = true;
    private boolean deferWrite = false;
    private boolean constraintsEnabled = true;

    // User-defined variables (e.g., @order_no)
    final Map<String, Object> userVariables = new HashMap<>();

    public long statementCount = 0L;
    public long totalExecutionTimeMs = 0L;
    private final long startTimeMs = System.currentTimeMillis();

    final SqlPrivilegeManager privilegeManager;
    final SqlTransactionManager transactionManager;
    final SqlDatabaseManager databaseManager;
    public final SqlSystemDatabaseManager systemDbManager;

    private final Map<String, Object> systemVariables = new LinkedHashMap<>();

    public DatabaseEngine(File baseDir) {
        this.storageEngine = new StorageEngine(baseDir);
        this.activeDatabaseName = null;
        this.activeSchemaJson = null;
        this.privilegeManager = new SqlPrivilegeManager(this);
        this.transactionManager = new SqlTransactionManager(this);
        this.databaseManager = new SqlDatabaseManager(this);
        this.systemDbManager = new SqlSystemDatabaseManager();
        initDefaultSystemVariables();
        loadUsers();
        initializeSystemSchemas();
    }

    private void initDefaultSystemVariables() {
        systemVariables.put("auto_increment_increment", "1");
        systemVariables.put("autocommit", "ON");
        systemVariables.put("character_set_client", "utf8mb4");
        systemVariables.put("character_set_connection", "utf8mb4");
        systemVariables.put("character_set_database", "utf8mb4");
        systemVariables.put("character_set_results", "utf8mb4");
        systemVariables.put("character_set_server", "utf8mb4");
        systemVariables.put("character_set_system", "utf8mb3");
        systemVariables.put("collation_connection", "utf8mb4_general_ci");
        systemVariables.put("collation_database", "utf8mb4_general_ci");
        systemVariables.put("collation_server", "utf8mb4_general_ci");
        systemVariables.put("foreign_key_checks", "ON");
        systemVariables.put("interactive_timeout", "28800");
        systemVariables.put("license", "GPL");
        systemVariables.put("log_bin", "OFF");
        systemVariables.put("log_bin_trust_function_creators", "1");
        systemVariables.put("max_allowed_packet", "67108864");
        systemVariables.put("max_connections", "151");
        systemVariables.put("net_buffer_length", "16384");
        systemVariables.put("port", "3306");
        systemVariables.put("protocol_version", "10");
        systemVariables.put("sql_mode", "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
        systemVariables.put("system_time_zone", "UTC");
        systemVariables.put("time_zone", "SYSTEM");
        systemVariables.put("version", SqlFunctions.getEngineVersion());
        systemVariables.put("version_comment", "PocketSQL Server");
        systemVariables.put("version_compile_os", "Android");
        systemVariables.put("version_compile_machine", "arm64");
        systemVariables.put("wait_timeout", "28800");
    }

    private void initializeSystemSchemas() {
        try {
            String[] systemDbs = {"information_schema", "pocketsql", "sys"};
            for (String db : systemDbs) {
                storageEngine.createDatabaseDir(db);
                JSONObject schemaJson = new JSONObject();
                List<String> tables = systemDbManager.getSystemTables(db);
                for (String tbl : tables) {
                    try {
                        TableData td = systemDbManager.getSystemTable(this, db, tbl);
                        JSONObject tableSchema = new JSONObject();
                        tableSchema.put("columns", new JSONArray(td.columns));
                        tableSchema.put("types", new JSONArray(td.types));
                        
                        JSONObject defaultsObj = new JSONObject();
                        JSONObject onUpdateObj = new JSONObject();
                        JSONObject nullablesObj = new JSONObject();
                        JSONObject keysObj = new JSONObject();
                        JSONObject extrasObj = new JSONObject();
                        
                        for (String col : td.columns) {
                            defaultsObj.put(col, JSONObject.NULL);
                            onUpdateObj.put(col, JSONObject.NULL);
                            nullablesObj.put(col, true);
                            
                            if ("user".equalsIgnoreCase(tbl) && ("host".equalsIgnoreCase(col) || "user".equalsIgnoreCase(col))) {
                                keysObj.put(col, "PRI");
                            } else if ("db".equalsIgnoreCase(tbl) && ("host".equalsIgnoreCase(col) || "db".equalsIgnoreCase(col) || "user".equalsIgnoreCase(col))) {
                                keysObj.put(col, "PRI");
                            } else {
                                keysObj.put(col, "");
                            }
                            
                            extrasObj.put(col, "");
                        }
                        
                        tableSchema.put("defaults", defaultsObj);
                        tableSchema.put("on_update", onUpdateObj);
                        tableSchema.put("nullables", nullablesObj);
                        tableSchema.put("keys", keysObj);
                        tableSchema.put("extras", extrasObj);
                        tableSchema.put("checks", new JSONArray());
                        tableSchema.put("foreign_keys", new JSONObject());
                        JSONArray pkArr = new JSONArray();
                        if ("pocketsql".equalsIgnoreCase(db)) {
                            if ("user".equalsIgnoreCase(tbl)) {
                                pkArr.put("Host");
                                pkArr.put("User");
                            } else if ("db".equalsIgnoreCase(tbl)) {
                                pkArr.put("Host");
                                pkArr.put("Db");
                                pkArr.put("User");
                            }
                        }
                        tableSchema.put("primary_key", pkArr);
                        tableSchema.put("uniques", new JSONArray());
                        
                        JSONObject indexesObj = new JSONObject();
                        if (pkArr.length() > 0) {
                            JSONObject idxMeta = new JSONObject();
                            idxMeta.put("name", "PRIMARY");
                            idxMeta.put("columns", pkArr);
                            idxMeta.put("unique", true);
                            idxMeta.put("type", "BTREE");
                            indexesObj.put("PRIMARY", idxMeta);
                        }
                        tableSchema.put("indexes", indexesObj);
                        
                        if ("sys".equalsIgnoreCase(db) && "version".equalsIgnoreCase(tbl)) {
                            tableSchema.put("is_view", true);
                            tableSchema.put("query", "SELECT '8.0.25' AS version, 'PocketSQL' AS source");
                        } else if ("information_schema".equalsIgnoreCase(db)) {
                            tableSchema.put("is_view", true);
                        }
                        
                        schemaJson.put(tbl, tableSchema);
                    } catch (Exception e) {
                        com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                    }
                }
                storageEngine.writeSchema(db, schemaJson);
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
    }    public void setCurrentUser(String username, String host) {
        this.currentUser = username;
        this.currentHost = host;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public Object getUserVariable(String name) {
        if (name == null) return null;
        String cleanName = name.toLowerCase().trim();
        if (cleanName.startsWith("@@global.")) {
            return getSystemVariable(cleanName.substring(9));
        } else if (cleanName.startsWith("@@session.")) {
            return getSystemVariable(cleanName.substring(10));
        } else if (cleanName.startsWith("@@persist.")) {
            return getSystemVariable(cleanName.substring(10));
        } else if (cleanName.startsWith("@@persist_only.")) {
            return getSystemVariable(cleanName.substring(15));
        } else if (cleanName.startsWith("@@")) {
            return getSystemVariable(cleanName.substring(2));
        } else if (cleanName.startsWith("@")) {
            return userVariables.get(cleanName);
        }
        return getSystemVariable(cleanName);
    }

    public Object getSystemVariable(String name) {
        if (name == null) return null;
        String cleanName = name.toLowerCase().trim();
        if (cleanName.startsWith("@@global.")) cleanName = cleanName.substring(9);
        else if (cleanName.startsWith("@@session.")) cleanName = cleanName.substring(10);
        else if (cleanName.startsWith("@@persist.")) cleanName = cleanName.substring(10);
        else if (cleanName.startsWith("@@persist_only.")) cleanName = cleanName.substring(15);
        else if (cleanName.startsWith("@@")) cleanName = cleanName.substring(2);

        Object val = systemVariables.get(cleanName);
        if (val != null) return val;
        if ("foreign_key_checks".equals(cleanName)) return foreignKeyChecks ? "1" : "0";
        return null;
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public void loadUsers() {
        try {
            this.cachedUsers = storageEngine.readUsers();
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
            this.cachedUsers = new JSONObject();
        }
    }

    public boolean authenticate(String username, String password) {
        try {
            loadUsers();
            
            String key = username + "@localhost";
            if (cachedUsers.has(key)) {
                JSONObject userObj = cachedUsers.getJSONObject(key);
                String stored = userObj.getString("password");
                if (SecurityHelper.verifyPassword(password, stored)) {
                    // Dynamic Upgrade: if legacy format (not starting with $argon2id$) was stored, upgrade it to Argon2id
                    if (!stored.startsWith("$argon2id$")) {
                        String newHash = SecurityHelper.hashPassword(password);
                        userObj.put("password", newHash);
                        storageEngine.writeUsers(cachedUsers);
                    }
                    setCurrentUser(username, "localhost");
                    return true;
                }
            }
            Iterator<String> keys = cachedUsers.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String[] parts = k.split("@");
                if (parts.length == 2 && parts[0].equals(username)) {
                    JSONObject userObj = cachedUsers.getJSONObject(k);
                    String stored = userObj.getString("password");
                    if (SecurityHelper.verifyPassword(password, stored)) {
                        // Dynamic Upgrade: if legacy format (not starting with $argon2id$) was stored, upgrade it to Argon2id
                        if (!stored.startsWith("$argon2id$")) {
                            String newHash = SecurityHelper.hashPassword(password);
                            userObj.put("password", newHash);
                            storageEngine.writeUsers(cachedUsers);
                        }
                        setCurrentUser(username, parts[1]);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
        return false;
    }

    public boolean hasUsersConfigured() {
        return storageEngine.usersFileExists();
    }

    public void initializeAdminUser(String username, String host, String password) throws Exception {
        privilegeManager.initializeAdminUser(username, host, password);
    }

    public void initializeDefaultRootUser() {
        privilegeManager.initializeDefaultRootUser();
    }

    void verifyPrivilege(String privilege, String db, String table) throws Exception {
        privilegeManager.verifyPrivilege(privilege, db, table);
    }

    boolean hasExactPrivilege(String privilege, String db, String table) {
        return privilegeManager.hasExactPrivilege(privilege, db, table);
    }

    public synchronized QueryResult execute(String sql) {
        long startTime = System.currentTimeMillis();
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        if (trimmed.isEmpty()) {
            return QueryResult.createError("Error: Empty query");
        }

        try {
            SqlScanner scanner = new SqlScanner(trimmed);
            List<SqlToken> tokens = scanner.scan();
            SqlParser parser = new SqlParser(tokens, trimmed);
            Command command = parser.parse();
            
            QueryResult result = command.execute(this);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            statementCount++;
            totalExecutionTimeMs += duration;
            
            // Add execution time to result
            return new QueryResult(
                result.success,
                result.message,
                result.columns,
                result.columnTypes,
                result.rows,
                result.affectedRows,
                duration
            );
        } catch (Throwable e) {
            Throwable syntaxError = e;
            while (syntaxError != null && !(syntaxError instanceof SqlSyntaxException)) {
                syntaxError = syntaxError.getCause();
            }
            if (syntaxError instanceof SqlSyntaxException) {
                String ver = SqlFunctions.getEngineVersion();
                return QueryResult.createError("ERROR 1064 (42000): You have an error in your SQL syntax; check the manual that corresponds to your PocketSQL server version (" + ver + ") for the right syntax near '" + syntaxError.getMessage() + "'");
            }
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            if (msg.startsWith("ERROR ") || msg.startsWith("ERROR:")) {
                return QueryResult.createError(msg);
            }
            return QueryResult.createError("ERROR 1105 (HY000): " + msg);
        }
    }

    public String getActiveDatabase() {
        return activeDatabaseName;
    }

    public StorageEngine getStorageEngine() {
        return storageEngine;
    }

    public String resolveDatabaseName(String dbName) {
        if (dbName == null) return null;
        List<String> dbs = storageEngine.listDatabases();
        for (String db : dbs) {
            if (db.equalsIgnoreCase(dbName)) {
                return db;
            }
        }
        return dbName;
    }

    public String resolveTableName(String tableName) {
        if (tableName == null) return null;
        try {
            ensureActiveSchema();
        } catch (Exception ignored) {}
        if (activeSchemaJson == null) return tableName;

        if (tableName.contains(".")) {
            int idx = tableName.indexOf('.');
            String db = tableName.substring(0, idx);
            String tbl = tableName.substring(idx + 1);
            return db + "." + resolveTableName(tbl);
        }

        Iterator<String> keys = activeSchemaJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.equalsIgnoreCase(tableName)) {
                return key;
            }
        }
        return tableName;
    }

    // --- DB Commands execution ---

    public QueryResult createDatabase(String dbName, boolean ifNotExists) throws Exception {
        return databaseManager.createDatabase(dbName, ifNotExists);
    }

    public QueryResult createDatabase(String dbName, boolean ifNotExists, String charset, String collation) throws Exception {
        return databaseManager.createDatabase(dbName, ifNotExists, charset, collation);
    }

    public QueryResult dropDatabase(String dbName, boolean ifExists) throws Exception {
        return databaseManager.dropDatabase(dbName, ifExists);
    }

    public QueryResult useDatabase(String dbName) throws Exception {
        return databaseManager.useDatabase(dbName);
    }

    public QueryResult showDatabases() throws Exception {
        return databaseManager.showDatabases(null, null);
    }

    public QueryResult showDatabases(String likePattern, Clause.Where where) throws Exception {
        return databaseManager.showDatabases(likePattern, where);
    }

    // --- Table Commands execution ---

    void checkActiveDatabase() throws Exception {
        if (activeDatabaseName == null) {
            throw new Exception("No database selected. Run 'USE <db_name>' first.");
        }
    }

    public QueryResult executeWith(Map<String, Command.Select> ctes, Command mainQuery) throws Exception {
        ensureActiveSchema();
        List<String> tempTables = new ArrayList<>();
        try {
            for (Map.Entry<String, Command.Select> entry : ctes.entrySet()) {
                String cteName = entry.getKey();
                QueryResult res = selectFrom(entry.getValue());
                if (!res.success) {
                    return res;
                }
                createTableFromQueryResult(cteName, res);
                tempTables.add(cteName);
            }
            return mainQuery.execute(this);
        } finally {
            for (String tbl : tempTables) {
                try {
                    dropTable(tbl, true);
                } catch (Exception ignored) {}
            }
        }
    }

    private void createTableFromQueryResult(String tableName, QueryResult qres) throws Exception {
        List<String> rawCols = qres.columns != null ? qres.columns : new ArrayList<>();
        List<String> cols = new ArrayList<>();
        for (String c : rawCols) {
            cols.add(getDisplayColumnName(c));
        }
        List<String> types = qres.columnTypes != null ? qres.columnTypes : new ArrayList<>();
        while (types.size() < cols.size()) {
            types.add("TEXT");
        }
        Map<String, String> colDefaults = new HashMap<>();
        Map<String, String> colOnUpdates = new HashMap<>();
        Map<String, Boolean> colNullables = new HashMap<>();
        Map<String, String> colKeys = new HashMap<>();
        Map<String, String> colExtras = new HashMap<>();
        for (String c : cols) {
            colNullables.put(c, true);
            colKeys.put(c, "");
        }
        createTable(tableName, cols, types, colDefaults, colOnUpdates, colNullables, colKeys, colExtras,
                    new ArrayList<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), false);

        if (qres.rows != null && !qres.rows.isEmpty()) {
            List<List<Object>> valuesList = new ArrayList<>();
            for (Map<String, Object> r : qres.rows) {
                List<Object> vals = new ArrayList<>();
                for (int i = 0; i < rawCols.size(); i++) {
                    String raw = rawCols.get(i);
                    String clean = cols.get(i);
                    Object val = r.get(clean);
                    if (val == null) val = r.get(raw);
                    if (val == null) val = getRowValue(r, clean);
                    if (val == null) val = getRowValue(r, raw);
                    vals.add(val);
                }
                valuesList.add(vals);
            }
            insertInto(tableName, cols, valuesList, null);
        }
        tableCache.remove(tableName);
    }

    public QueryResult insertIntoSelect(String tableName, List<String> columnNames, Command.Select selectQuery) throws Exception {
        return insertIntoSelect(tableName, columnNames, selectQuery, false);
    }

    public QueryResult insertIntoSelect(String tableName, List<String> columnNames, Command.Select selectQuery, boolean ignore) throws Exception {
        QueryResult selectRes = selectFrom(selectQuery);
        if (!selectRes.success) {
            return selectRes;
        }
        TableData targetTd = getOrLoadTable(tableName);
        List<String> targetCols = columnNames != null ? columnNames : new ArrayList<>(targetTd.columns);

        List<List<Object>> valuesList = new ArrayList<>();
        for (Map<String, Object> selectRow : selectRes.rows) {
            List<Object> rowVals = new ArrayList<>();
            for (String col : selectRes.columns) {
                Object val = selectRow.get(col);
                if (val == null) {
                    val = getRowValue(selectRow, col);
                }
                rowVals.add(val);
            }
            valuesList.add(rowVals);
        }
        return insertInto(tableName, targetCols, valuesList, null, ignore);
    }

    public QueryResult createTableAsSelect(String tableName, Command.Select selectQuery, boolean ifNotExists) throws Exception {
        QueryResult selectRes = selectFrom(selectQuery);
        if (!selectRes.success) {
            return selectRes;
        }
        createTableFromQueryResult(tableName, selectRes);
        return QueryResult.createSuccess("Table '" + tableName + "' created successfully", selectRes.rows != null ? selectRes.rows.size() : 0, 0);
    }

    public QueryResult createTable(String tableName, List<String> colNames, List<String> colTypes,
                                   Map<String, String> colDefaults, Map<String, String> colOnUpdates,
                                   Map<String, Boolean> colNullables, Map<String, String> colKeys,
                                   Map<String, String> colExtras, List<Map<String, Object>> checks,
                                   Map<String, String> foreignKeys,
                                   List<String> primaryKey, List<List<String>> uniques,
                                   boolean ifNotExists) throws Exception {
        return createTable(tableName, colNames, colTypes, colDefaults, colOnUpdates, colNullables,
                           colKeys, colExtras, checks, foreignKeys, primaryKey, uniques, ifNotExists,
                           null, null);
    }

    public QueryResult createTable(String tableName, List<String> colNames, List<String> colTypes,
                                   Map<String, String> colDefaults, Map<String, String> colOnUpdates,
                                   Map<String, Boolean> colNullables, Map<String, String> colKeys,
                                   Map<String, String> colExtras, List<Map<String, Object>> checks,
                                   Map<String, String> foreignKeys,
                                   List<String> primaryKey, List<List<String>> uniques,
                                   boolean ifNotExists, String charset, String collation) throws Exception {
        return createTable(tableName, colNames, colTypes, colDefaults, colOnUpdates, colNullables,
                           colKeys, colExtras, checks, foreignKeys, primaryKey, uniques, ifNotExists,
                           charset, collation, null);
    }

    public QueryResult createTable(String tableName, List<String> colNames, List<String> colTypes,
                                   Map<String, String> colDefaults, Map<String, String> colOnUpdates,
                                   Map<String, Boolean> colNullables, Map<String, String> colKeys,
                                   Map<String, String> colExtras, List<Map<String, Object>> checks,
                                   Map<String, String> foreignKeys,
                                   List<String> primaryKey, List<List<String>> uniques,
                                   boolean ifNotExists, String charset, String collation,
                                   Map<String, SqlAttributes> columnAttributes) throws Exception {
        return createTable(tableName, colNames, colTypes, colDefaults, colOnUpdates, colNullables,
                           colKeys, colExtras, checks, foreignKeys, primaryKey, uniques, ifNotExists,
                           charset, collation, columnAttributes, null);
    }

    public QueryResult createTable(String tableName, List<String> colNames, List<String> colTypes,
                                   Map<String, String> colDefaults, Map<String, String> colOnUpdates,
                                   Map<String, Boolean> colNullables, Map<String, String> colKeys,
                                   Map<String, String> colExtras, List<Map<String, Object>> checks,
                                   Map<String, String> foreignKeys,
                                   List<String> primaryKey, List<List<String>> uniques,
                                   boolean ifNotExists, String charset, String collation,
                                   Map<String, SqlAttributes> columnAttributes,
                                   Map<List<String>, String> uniqueNames) throws Exception {
        return createTable(tableName, colNames, colTypes, colDefaults, colOnUpdates, colNullables,
                           colKeys, colExtras, checks, foreignKeys, primaryKey, uniques, ifNotExists,
                           charset, collation, columnAttributes, uniqueNames, null);
    }

    public QueryResult createTable(String tableName, List<String> colNames, List<String> colTypes,
                                   Map<String, String> colDefaults, Map<String, String> colOnUpdates,
                                   Map<String, Boolean> colNullables, Map<String, String> colKeys,
                                   Map<String, String> colExtras, List<Map<String, Object>> checks,
                                   Map<String, String> foreignKeys,
                                   List<String> primaryKey, List<List<String>> uniques,
                                   boolean ifNotExists, String charset, String collation,
                                   Map<String, SqlAttributes> columnAttributes,
                                   Map<List<String>, String> uniqueNames,
                                   String definition) throws Exception {
        verifyPrivilege("CREATE", activeDatabaseName, tableName);
        ensureActiveSchema();

        String resolved = resolveTableName(tableName);
        if (activeSchemaJson.has(resolved)) {
            if (ifNotExists) {
                return QueryResult.createSuccess("Table already exists (ignored)", 0, 0);
            }
            return QueryResult.createError("Error: Table '" + tableName + "' already exists");
        }

        // Write schema details
        JSONObject tableSchema = new JSONObject();
        tableSchema.put("columns", new JSONArray(colNames));
        tableSchema.put("types", new JSONArray(colTypes));

        // Save default character set and collation in table level
        String finalCharset = charset;
        String finalCollation = collation;
        
        // Resolve database defaults if not specified at table level
        JSONObject dbMetadata = activeSchemaJson.optJSONObject("__db_metadata__");
        String dbCharset = "utf8mb4";
        String dbCollation = "utf8mb4_0900_ai_ci";
        if (dbMetadata != null) {
            dbCharset = dbMetadata.optString("default_character_set", dbCharset);
            dbCollation = dbMetadata.optString("default_collation", dbCollation);
        }
        
        if (finalCharset == null && finalCollation == null) {
            finalCharset = dbCharset;
            finalCollation = dbCollation;
        } else if (finalCharset != null && finalCollation == null) {
            finalCollation = SqlCollation.getDefaultCollationForCharset(finalCharset);
        } else if (finalCharset == null && finalCollation != null) {
            finalCharset = SqlCollation.getCharsetForCollation(finalCollation);
        }
        
        // Validate
        if (finalCharset != null) {
            if (!SqlCollation.isValidCharset(finalCharset)) {
                throw new Exception("Error: Unknown character set: '" + finalCharset + "'");
            }
        }
        if (finalCollation != null) {
            if (!SqlCollation.isValidCollation(finalCollation)) {
                throw new Exception("Error: Unknown collation: '" + finalCollation + "'");
            }
        }
        
        tableSchema.put("charset", finalCharset.toLowerCase());
        tableSchema.put("collation", finalCollation.toLowerCase());
        
        JSONObject defaultsObj = new JSONObject();
        if (colDefaults != null) {
            for (Map.Entry<String, String> entry : colDefaults.entrySet()) {
                defaultsObj.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
        }
        tableSchema.put("defaults", defaultsObj);

        JSONObject onUpdateObj = new JSONObject();
        if (colOnUpdates != null) {
            for (Map.Entry<String, String> entry : colOnUpdates.entrySet()) {
                onUpdateObj.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
        }
        tableSchema.put("on_update", onUpdateObj);

        JSONObject nullablesObj = new JSONObject();
        if (colNullables != null) {
            for (Map.Entry<String, Boolean> entry : colNullables.entrySet()) {
                nullablesObj.put(entry.getKey(), entry.getValue());
            }
        }
        tableSchema.put("nullables", nullablesObj);

        JSONObject keysObj = new JSONObject();
        if (colKeys != null) {
            for (Map.Entry<String, String> entry : colKeys.entrySet()) {
                keysObj.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
        }
        tableSchema.put("keys", keysObj);

        JSONObject extrasObj = new JSONObject();
        if (colExtras != null) {
            for (Map.Entry<String, String> entry : colExtras.entrySet()) {
                extrasObj.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
        }
        tableSchema.put("extras", extrasObj);

        JSONArray checksArr = new JSONArray();
        if (checks != null) {
            for (Map<String, Object> check : checks) {
                checksArr.put(new JSONObject(check));
            }
        }
        tableSchema.put("checks", checksArr);

        JSONObject fkObj = new JSONObject();
        if (foreignKeys != null) {
            for (Map.Entry<String, String> entry : foreignKeys.entrySet()) {
                fkObj.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
        }
        tableSchema.put("foreign_keys", fkObj);

        tableSchema.put("primary_key", primaryKey != null ? new JSONArray(primaryKey) : new JSONArray());

        JSONArray uniquesArr = new JSONArray();
        if (uniques != null) {
            for (List<String> group : uniques) {
                uniquesArr.put(new JSONArray(group));
            }
        }
        tableSchema.put("uniques", uniquesArr);

        JSONObject indexesObj = new JSONObject();
        if (primaryKey != null && !primaryKey.isEmpty()) {
            JSONObject idxMeta = new JSONObject();
            idxMeta.put("name", "PRIMARY");
            idxMeta.put("columns", new JSONArray(primaryKey));
            idxMeta.put("unique", true);
            idxMeta.put("type", "BTREE");
            indexesObj.put("PRIMARY", idxMeta);
        }
        if (uniques != null) {
            for (List<String> group : uniques) {
                if (group.isEmpty()) continue;
                String name = null;
                if (uniqueNames != null) {
                    name = uniqueNames.get(group);
                }
                if (name == null) {
                    name = "uq_" + String.join("_", group);
                }
                JSONObject idxMeta = new JSONObject();
                idxMeta.put("name", name);
                idxMeta.put("columns", new JSONArray(group));
                idxMeta.put("unique", true);
                idxMeta.put("type", "BTREE");
                indexesObj.put(name, idxMeta);
            }
        }
        if (colKeys != null) {
            for (Map.Entry<String, String> entry : colKeys.entrySet()) {
                if ("UNI".equalsIgnoreCase(entry.getValue())) {
                    String col = entry.getKey();
                    boolean alreadyInUniques = false;
                    if (uniques != null) {
                        for (List<String> group : uniques) {
                            if (group.size() == 1 && group.get(0).equalsIgnoreCase(col)) {
                                alreadyInUniques = true;
                                break;
                            }
                        }
                    }
                    if (!alreadyInUniques) {
                        String name = "uq_" + col;
                        JSONObject idxMeta = new JSONObject();
                        idxMeta.put("name", name);
                        idxMeta.put("columns", new JSONArray(Collections.singletonList(col)));
                        idxMeta.put("unique", true);
                        idxMeta.put("type", "BTREE");
                        indexesObj.put(name, idxMeta);
                    }
                }
            }
        }
        tableSchema.put("indexes", indexesObj);

        JSONObject attributesObj = new JSONObject();
        if (columnAttributes != null) {
            for (Map.Entry<String, SqlAttributes> entry : columnAttributes.entrySet()) {
                if (entry.getValue() != null) {
                    attributesObj.put(entry.getKey(), entry.getValue().toJsonObject());
                }
            }
        }
        tableSchema.put("attributes", attributesObj);
        if (definition != null) {
            tableSchema.put("definition", definition);
        }
        
        activeSchemaJson.put(tableName, tableSchema);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        // Initialize empty rows file
        storageEngine.writeTableRows(activeDatabaseName, tableName, new JSONArray());

        // Cache empty table
        TableData td = new TableData(tableName, colNames, colTypes);
        tableCache.put(tableName, td);

        return QueryResult.createSuccess("Table created successfully", 0, 0);
    }

    public QueryResult dropTable(String tableName, boolean ifExists) throws Exception {
        String resolvedName = resolveTableName(tableName);
        verifyPrivilege("DROP", activeDatabaseName, resolvedName != null ? resolvedName : tableName);
        ensureActiveSchema();

        if (resolvedName == null || !activeSchemaJson.has(resolvedName)) {
            if (ifExists) {
                return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
            }
            return QueryResult.createError("ERROR 1051 (42000): Unknown table '" + activeDatabaseName + "." + tableName + "'");
        }

        JSONObject ts = activeSchemaJson.getJSONObject(resolvedName);
        if (ts.optBoolean("is_view", false)) {
            return QueryResult.createError("ERROR 1347 (42000): '" + activeDatabaseName + "." + resolvedName + "' is a VIEW, use DROP VIEW");
        }

        activeSchemaJson.remove(resolvedName);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);
        storageEngine.deleteTableFile(activeDatabaseName, resolvedName);
        tableCache.remove(resolvedName);

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult dropTables(List<String> tableNames, boolean ifExists) throws Exception {
        if (tableNames == null || tableNames.isEmpty()) {
            return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
        }
        QueryResult lastRes = null;
        for (String tbl : tableNames) {
            lastRes = dropTable(tbl, ifExists);
            if (!lastRes.success && !ifExists) {
                return lastRes;
            }
        }
        return lastRes != null ? lastRes : QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult showTables() throws Exception {
        return showTables(null, false, null);
    }

    public QueryResult showTables(boolean full, Clause.Where where) throws Exception {
        return showTables(null, full, where);
    }

    public QueryResult showTables(String databaseName, boolean full, Clause.Where where) throws Exception {
        String dbToUse = databaseName != null ? databaseName : activeDatabaseName;
        if (dbToUse == null) {
            throw new Exception("No database selected");
        }

        List<String> columns = new ArrayList<>();
        List<String> types = new ArrayList<>();
        String colName = "Tables_in_" + dbToUse;
        columns.add(colName);
        types.add("VARCHAR");
        if (full) {
            columns.add("Table_type");
            types.add("VARCHAR");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> tables = new ArrayList<>();

        if (systemDbManager.isSystemDatabase(dbToUse)) {
            tables = systemDbManager.getSystemTables(dbToUse);
        } else {
            JSONObject schemaJson;
            if (dbToUse.equalsIgnoreCase(activeDatabaseName) && activeSchemaJson != null) {
                schemaJson = activeSchemaJson;
            } else {
                schemaJson = storageEngine.readSchema(dbToUse);
            }
            Iterator<String> keys = schemaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.startsWith("__")) {
                    tables.add(key);
                }
            }
            Collections.sort(tables);
        }

        for (String table : tables) {
            Map<String, Object> row = new HashMap<>();
            row.put(colName, table);
            row.put(colName.toLowerCase(), table);
            row.put(colName.toUpperCase(), table);
            row.put("table_name", table);
            row.put("TABLE_NAME", table);
            row.put("Table_name", table);
            row.put("name", table);
            row.put("NAME", table);
            row.put("Name", table);

            if (full) {
                String type = "BASE TABLE";
                if (systemDbManager.isSystemDatabase(dbToUse)) {
                    type = systemDbManager.getTableType(dbToUse, table);
                } else {
                    JSONObject schemaJson;
                    if (dbToUse.equalsIgnoreCase(activeDatabaseName) && activeSchemaJson != null) {
                        schemaJson = activeSchemaJson;
                    } else {
                        schemaJson = storageEngine.readSchema(dbToUse);
                    }
                    JSONObject ts = schemaJson.optJSONObject(table);
                    if (ts != null && ts.optBoolean("is_view", false)) {
                        type = "VIEW";
                    }
                }
                row.put("Table_type", type);
                row.put("table_type", type);
                row.put("TABLE_TYPE", type);
            }

            if (where == null || where.evaluate(row, null, this)) {
                Map<String, Object> displayRow = new HashMap<>();
                displayRow.put(colName, table);
                if (full) {
                    displayRow.put("Table_type", row.get("Table_type"));
                }
                rows.add(displayRow);
            }
        }

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }


    public QueryResult createView(String viewName, String selectQuery) throws Exception {
        return createView(viewName, selectQuery, null);
    }

    public QueryResult createView(String viewName, String selectQuery, String definition) throws Exception {
        viewName = resolveTableName(viewName);
        verifyPrivilege("CREATE", activeDatabaseName, viewName);
        ensureActiveSchema();

        JSONObject existing = activeSchemaJson.optJSONObject(viewName);
        if (existing != null && !existing.optBoolean("is_view", false)) {
            return QueryResult.createError("Error: Table '" + viewName + "' already exists");
        }

        // Validate view select query columns and tables
        SqlScanner scanner = new SqlScanner(selectQuery);
        List<SqlToken> tokens = scanner.scan();
        SqlParser parser = new SqlParser(tokens, selectQuery);
        Command cmd = parser.parse();
        if (cmd instanceof Command.Select) {
            validateColumnReferences((Command.Select) cmd);
        } else {
            throw new Exception("View select query must be a SELECT statement");
        }

        JSONObject viewSchema = new JSONObject();
        viewSchema.put("is_view", true);
        viewSchema.put("query", selectQuery);
        if (definition != null) {
            viewSchema.put("definition", definition);
        }

        activeSchemaJson.put(viewName, viewSchema);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        return QueryResult.createSuccess("View '" + viewName + "' created successfully", 0, 0);
    }

    public QueryResult dropView(String viewName, boolean ifExists) throws Exception {
        viewName = resolveTableName(viewName);
        verifyPrivilege("DROP", activeDatabaseName, viewName);
        ensureActiveSchema();

        if (!activeSchemaJson.has(viewName)) {
            if (ifExists) {
                return QueryResult.createSuccess("View does not exist (ignored)", 0, 0);
            }
            return QueryResult.createError("Error: View '" + viewName + "' does not exist");
        }

        JSONObject ts = activeSchemaJson.getJSONObject(viewName);
        if (!ts.optBoolean("is_view", false)) {
            return QueryResult.createError("Error: '" + viewName + "' is not a VIEW");
        }

        activeSchemaJson.remove(viewName);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);
        tableCache.remove(viewName);

        return QueryResult.createSuccess("View dropped successfully", 0, 0);
    }

    public QueryResult dropViews(List<String> viewNames, boolean ifExists) throws Exception {
        if (viewNames == null || viewNames.isEmpty()) {
            return QueryResult.createSuccess("No views specified to drop", 0, 0);
        }
        QueryResult lastRes = null;
        for (String view : viewNames) {
            lastRes = dropView(view, ifExists);
            if (!lastRes.success && !ifExists) {
                return lastRes;
            }
        }
        return QueryResult.createSuccess("View(s) dropped successfully", 0, 0);
    }

    public QueryResult createProcedure(String procName, String procDef) throws Exception {
        SqlScanner scanner = new SqlScanner(procDef);
        List<SqlToken> tokens = scanner.scan();

        Set<String> parameterNames = new java.util.HashSet<>();
        int pos = 0;
        // Skip until the parameter list starting '('
        while (pos < tokens.size() && !tokens.get(pos).value.equals("(")) {
            pos++;
        }
        if (pos < tokens.size() && tokens.get(pos).value.equals("(")) {
            pos++; // consume '('
            int parenDepth = 1;
            List<List<SqlToken>> paramsTokens = new ArrayList<>();
            List<SqlToken> currentParam = new ArrayList<>();
            while (pos < tokens.size()) {
                SqlToken t = tokens.get(pos);
                if (t.value.equals("(")) {
                    parenDepth++;
                } else if (t.value.equals(")")) {
                    parenDepth--;
                    if (parenDepth == 0) {
                        if (!currentParam.isEmpty()) {
                            paramsTokens.add(currentParam);
                        }
                        pos++; // consume ')'
                        break;
                    }
                }
                
                if (parenDepth == 1 && t.value.equals(",")) {
                    if (!currentParam.isEmpty()) {
                        paramsTokens.add(currentParam);
                        currentParam = new ArrayList<>();
                    }
                } else {
                    currentParam.add(t);
                }
                pos++;
            }
            
            for (List<SqlToken> param : paramsTokens) {
                if (param.isEmpty()) continue;
                int pIdx = 0;
                String firstVal = param.get(pIdx).value.toUpperCase();
                if (firstVal.equals("IN") || firstVal.equals("OUT") || firstVal.equals("INOUT")) {
                    pIdx++;
                }
                if (pIdx < param.size()) {
                    SqlToken nameToken = param.get(pIdx);
                    if (nameToken.type == SqlToken.Type.IDENTIFIER || nameToken.type == SqlToken.Type.KEYWORD) {
                        parameterNames.add(nameToken.value.toLowerCase());
                    }
                }
            }
        }

        // Validate table and column references in procedure body
        validateProcedureOrFunction(tokens, parameterNames);

        return createCatalogObject(procName, procDef, "__procedures__", "Procedure");
    }

    public QueryResult dropProcedure(String procName, boolean ifExists) throws Exception {
        return dropCatalogObject(procName, "__procedures__", "Procedure", ifExists);
    }

    public QueryResult callProcedure(String procName, List<Object> args) throws Exception {
        ensureActiveSchema();
        JSONObject procs = activeSchemaJson.optJSONObject("__procedures__");
        if (procs == null || !procs.has(procName)) {
            return QueryResult.createError("Error: Procedure '" + procName + "' does not exist");
        }
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String) {
                    String strArg = (String) arg;
                    if (strArg.startsWith("@")) {
                        userVariables.put(strArg.toLowerCase(), 1L);
                    }
                }
            }
        }
        return QueryResult.createSuccess("Procedure '" + procName + "' called successfully (execution is mock-only)", 0, 0);
    }

    public QueryResult createTrigger(String triggerName, String triggerDef) throws Exception {
        return createCatalogObject(triggerName, triggerDef, "__triggers__", "Trigger");
    }

    public QueryResult dropTrigger(String triggerName, boolean ifExists) throws Exception {
        return dropCatalogObject(triggerName, "__triggers__", "Trigger", ifExists);
    }

    public QueryResult createEvent(String eventName, String eventDef) throws Exception {
        return createCatalogObject(eventName, eventDef, "__events__", "Event");
    }

    public QueryResult dropEvent(String eventName, boolean ifExists) throws Exception {
        return dropCatalogObject(eventName, "__events__", "Event", ifExists);
    }

    public QueryResult showProcedureStatus(Clause.Where where) throws Exception {
        verifyPrivilege("SELECT", activeDatabaseName, "*");
        ensureActiveSchema();

        List<String> columns = new ArrayList<>();
        columns.add("Db");
        columns.add("Name");
        columns.add("Type");
        columns.add("Definer");
        columns.add("Modified");
        columns.add("Created");
        columns.add("Security_type");
        columns.add("Comment");
        columns.add("character_set_client");
        columns.add("collation_connection");
        columns.add("Database Collation");

        List<String> types = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            types.add("TEXT");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        JSONObject procs = activeSchemaJson.optJSONObject("__procedures__");
        if (procs != null) {
            Iterator<String> keys = procs.keys();
            while (keys.hasNext()) {
                String procName = keys.next();
                JSONObject procObj = procs.getJSONObject(procName);
                String db = procObj.optString("db", activeDatabaseName);

                Map<String, Object> row = new HashMap<>();
                row.put("Db", db);
                row.put("db", db);
                row.put("DB", db);
                
                row.put("Name", procName);
                row.put("name", procName);
                row.put("NAME", procName);
                
                row.put("Type", "PROCEDURE");
                row.put("type", "PROCEDURE");
                row.put("TYPE", "PROCEDURE");
                
                row.put("Definer", "root@localhost");
                row.put("DEFINER", "root@localhost");
                
                String timeStr = "2026-05-30 00:00:00";
                row.put("Modified", timeStr);
                row.put("Created", timeStr);
                row.put("Security_type", "DEFINER");
                row.put("Comment", "");
                row.put("character_set_client", "utf8mb4");
                row.put("collation_connection", "utf8mb4_0900_ai_ci");
                row.put("Database Collation", "utf8mb4_0900_ai_ci");

                if (where == null || where.evaluate(row, null, this)) {
                    rows.add(row);
                }
            }
        }

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public QueryResult showCreateProcedure(String procName) throws Exception {
        verifyPrivilege("SELECT", activeDatabaseName, "*");
        ensureActiveSchema();

        JSONObject procs = activeSchemaJson.optJSONObject("__procedures__");
        if (procs == null || !procs.has(procName)) {
            return QueryResult.createError("Error: Procedure '" + procName + "' does not exist");
        }

        JSONObject procObj = procs.getJSONObject(procName);
        String definition = procObj.getString("definition");

        List<String> columns = new ArrayList<>();
        columns.add("Procedure");
        columns.add("sql_mode");
        columns.add("Create Procedure");
        columns.add("character_set_client");
        columns.add("collation_connection");
        columns.add("Database Collation");

        List<String> types = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            types.add("TEXT");
        }

        Map<String, Object> row = new HashMap<>();
        row.put("Procedure", procName);
        row.put("sql_mode", "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES");
        row.put("Create Procedure", SqlFormatter.formatSql(definition));
        row.put("character_set_client", "utf8mb4");
        row.put("collation_connection", "utf8mb4_0900_ai_ci");
        row.put("Database Collation", "utf8mb4_0900_ai_ci");

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public synchronized QueryResult showCreateView(String viewName) throws Exception {
        viewName = resolveTableName(viewName);
        verifyPrivilege("SELECT", activeDatabaseName, viewName);
        ensureActiveSchema();

        if (!activeSchemaJson.has(viewName)) {
            return QueryResult.createError("Error: View '" + viewName + "' does not exist");
        }

        JSONObject viewObj = activeSchemaJson.getJSONObject(viewName);
        if (!viewObj.optBoolean("is_view", false)) {
            return QueryResult.createError("Error: '" + viewName + "' is not a VIEW");
        }

        String definition = "CREATE VIEW " + viewName + " AS " + viewObj.optString("query", "");

        List<String> columns = new ArrayList<>();
        columns.add("View");
        columns.add("Create View");
        columns.add("character_set_client");
        columns.add("collation_connection");

        List<String> types = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            types.add("TEXT");
        }

        Map<String, Object> row = new HashMap<>();
        row.put("View", viewName);
        row.put("Create View", SqlFormatter.formatSql(definition));
        row.put("character_set_client", "utf8mb4");
        row.put("collation_connection", "utf8mb4_0900_ai_ci");

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public synchronized QueryResult showCreateFunction(String functionName) throws Exception {
        verifyPrivilege("SELECT", activeDatabaseName, "*");
        ensureActiveSchema();

        JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
        String exactName = null;
        if (fns != null) {
            Iterator<String> keys = fns.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.equalsIgnoreCase(functionName)) {
                    exactName = k;
                    break;
                }
            }
        }

        if (exactName == null) {
            return QueryResult.createError("Error: Function '" + functionName + "' does not exist");
        }

        JSONObject funcObj = fns.getJSONObject(exactName);
        
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE FUNCTION ").append(exactName).append("(");
        JSONArray params = funcObj.optJSONArray("parameters");
        if (params != null) {
            for (int i = 0; i < params.length(); i++) {
                if (i > 0) sb.append(", ");
                JSONObject param = params.optJSONObject(i);
                sb.append(param.optString("name")).append(" ").append(param.optString("type"));
            }
        }
        sb.append(") RETURNS ").append(funcObj.optString("returnType")).append("\nBEGIN\n    ");
        
        JSONArray bodyArr = funcObj.optJSONArray("body");
        if (bodyArr != null) {
            for (int i = 0; i < bodyArr.length(); i++) {
                JSONObject tok = bodyArr.optJSONObject(i);
                if (tok != null) {
                    sb.append(tok.optString("value", "")).append(" ");
                }
            }
        }
        sb.append("\nEND");

        String definition = sb.toString();

        List<String> columns = new ArrayList<>();
        columns.add("Function");
        columns.add("sql_mode");
        columns.add("Create Function");
        columns.add("character_set_client");
        columns.add("collation_connection");
        columns.add("Database Collation");

        List<String> types = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            types.add("TEXT");
        }

        Map<String, Object> row = new HashMap<>();
        row.put("Function", exactName);
        row.put("sql_mode", "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES");
        row.put("Create Function", SqlFormatter.formatSql(definition));
        row.put("character_set_client", "utf8mb4");
        row.put("collation_connection", "utf8mb4_0900_ai_ci");
        row.put("Database Collation", "utf8mb4_0900_ai_ci");

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public QueryResult describeTable(String tableName) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("SELECT", activeDatabaseName, tableName);
        ensureActiveSchema();

        String dbToCheck = activeDatabaseName;
        String tableToCheck = tableName;
        if (tableName.contains(".")) {
            int dotIdx = tableName.indexOf('.');
            dbToCheck = tableName.substring(0, dotIdx).trim();
            tableToCheck = tableName.substring(dotIdx + 1).trim();
        }

        if (dbToCheck != null && systemDbManager.isSystemDatabase(dbToCheck)) {
            TableData td = systemDbManager.getSystemTable(this, dbToCheck.toLowerCase(), tableToCheck.toLowerCase());
            List<String> columns = new ArrayList<>();
            columns.add("Field");
            columns.add("Type");
            columns.add("Null");
            columns.add("Key");
            columns.add("Default");
            columns.add("Extra");
            
            List<String> types = new ArrayList<>();
            for (int i = 0; i < 6; i++) types.add("TEXT");

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < td.columns.size(); i++) {
                String colName = td.columns.get(i);
                Map<String, Object> row = new HashMap<>();
                row.put("Field", colName);
                row.put("Type", td.types.get(i));
                row.put("Null", "YES");
                row.put("Key", "");
                row.put("Default", null);
                row.put("Extra", "");
                rows.add(row);
            }
            return QueryResult.createSelectSuccess(columns, types, rows, 0);
        }

        if (!activeSchemaJson.has(tableName)) {
            return QueryResult.createError("Error: Table '" + tableName + "' does not exist");
        }

        JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
        if (tableSchema.optBoolean("is_view", false)) {
            TableData td = getOrLoadTable(tableName);
            List<String> columns = new ArrayList<>();
            columns.add("Field");
            columns.add("Type");
            columns.add("Null");
            columns.add("Key");
            columns.add("Default");
            columns.add("Extra");
            
            List<String> types = new ArrayList<>();
            for (int i = 0; i < 6; i++) types.add("TEXT");

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < td.columns.size(); i++) {
                String colName = td.columns.get(i);
                Map<String, Object> row = new HashMap<>();
                row.put("Field", colName);
                row.put("Type", td.types.get(i));
                row.put("Null", "YES");
                row.put("Key", "");
                row.put("Default", null);
                row.put("Extra", "");
                rows.add(row);
            }
            return QueryResult.createSelectSuccess(columns, types, rows, 0);
        }

        JSONArray colsArr = tableSchema.getJSONArray("columns");
        JSONArray typesArr = tableSchema.getJSONArray("types");
        JSONObject defaultsObj = tableSchema.optJSONObject("defaults");

        List<String> columns = new ArrayList<>();
        columns.add("Field");
        columns.add("Type");
        columns.add("Null");
        columns.add("Key");
        columns.add("Default");
        columns.add("Extra");
        
        List<String> types = new ArrayList<>();
        types.add("TEXT");
        types.add("TEXT");
        types.add("TEXT");
        types.add("TEXT");
        types.add("TEXT");
        types.add("TEXT");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < colsArr.length(); i++) {
            String colName = colsArr.getString(i);
            Map<String, Object> row = new HashMap<>();
            row.put("Field", colName);
            row.put("Type", typesArr.getString(i));
            row.put("Null", isColumnNullable(tableSchema, colName) ? "YES" : "NO");
            row.put("Key", getColumnKeyType(tableSchema, colName));
            
            String defVal = null;
            if (defaultsObj != null && defaultsObj.has(colName)) {
                Object rawDef = defaultsObj.get(colName);
                if (rawDef != JSONObject.NULL) {
                    defVal = rawDef.toString();
                }
            }
            row.put("Default", defVal);
            row.put("Extra", getColumnExtraInfo(tableSchema, colName));
            rows.add(row);
        }

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public synchronized QueryResult showIndexes(String tableName) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("SELECT", activeDatabaseName, tableName);
        ensureActiveSchema();

        if (!activeSchemaJson.has(tableName)) {
            return QueryResult.createError("Error: Table '" + tableName + "' does not exist");
        }

        List<String> cols = new java.util.ArrayList<>();
        cols.add("Table");
        cols.add("Non_unique");
        cols.add("Key_name");
        cols.add("Seq_in_index");
        cols.add("Column_name");
        cols.add("Collation");
        cols.add("Cardinality");
        cols.add("Sub_part");
        cols.add("Packed");
        cols.add("Null");
        cols.add("Index_type");
        cols.add("Comment");
        cols.add("Index_comment");
        cols.add("Visible");
        cols.add("Expression");

        List<String> typs = new java.util.ArrayList<>();
        typs.add("VARCHAR"); // Table
        typs.add("BIGINT");  // Non_unique
        typs.add("VARCHAR"); // Key_name
        typs.add("BIGINT");  // Seq_in_index
        typs.add("VARCHAR"); // Column_name
        typs.add("VARCHAR"); // Collation
        typs.add("BIGINT");  // Cardinality
        typs.add("BIGINT");  // Sub_part
        typs.add("VARCHAR"); // Packed
        typs.add("VARCHAR"); // Null
        typs.add("VARCHAR"); // Index_type
        typs.add("VARCHAR"); // Comment
        typs.add("VARCHAR"); // Index_comment
        typs.add("VARCHAR"); // Visible
        typs.add("VARCHAR"); // Expression

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        JSONObject ts = activeSchemaJson.getJSONObject(tableName);
        JSONObject indexesObj = ts.optJSONObject("indexes");
        if (indexesObj != null) {
            List<String> idxNames = new java.util.ArrayList<>();
            Iterator<String> it = indexesObj.keys();
            while (it.hasNext()) {
                idxNames.add(it.next());
            }
            Collections.sort(idxNames, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    if ("PRIMARY".equalsIgnoreCase(a)) return -1;
                    if ("PRIMARY".equalsIgnoreCase(b)) return 1;
                    return a.compareToIgnoreCase(b);
                }
            });

            for (String idxName : idxNames) {
                JSONObject idxMeta = indexesObj.optJSONObject(idxName);
                if (idxMeta == null) continue;

                JSONArray columnsArr = idxMeta.optJSONArray("columns");
                if (columnsArr == null) continue;

                boolean unique = idxMeta.optBoolean("unique", false);
                long nonUnique = unique ? 0L : 1L;
                String idxType = idxMeta.optString("type", "BTREE");

                for (int i = 0; i < columnsArr.length(); i++) {
                    String col = columnsArr.getString(i);
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("Table", tableName);
                    row.put("Non_unique", nonUnique);
                    row.put("Key_name", idxName);
                    row.put("Seq_in_index", (long) (i + 1));
                    row.put("Column_name", col);
                    row.put("Collation", "A");
                    row.put("Cardinality", 0L);
                    row.put("Sub_part", null);
                    row.put("Packed", null);
                    
                    boolean nullable = true;
                    JSONObject nullables = ts.optJSONObject("nullables");
                    if (nullables != null && nullables.has(col)) {
                        nullable = nullables.optBoolean(col, true);
                    }
                    row.put("Null", nullable ? "YES" : "");
                    row.put("Index_type", idxType);
                    row.put("Comment", "");
                    row.put("Index_comment", "");
                    row.put("Visible", "YES");
                    row.put("Expression", null);
                    
                    rows.add(row);
                }
            }
        }

        return QueryResult.createSelectSuccess(cols, typs, rows, 0);
    }

    TableData getOrLoadTable(String tableName) throws Exception {
        boolean isLegacySystemTable = tableName.contains(".") && 
            (tableName.equalsIgnoreCase("INFORMATION_SCHEMA.STATISTICS") || 
             tableName.equalsIgnoreCase("INFORMATION_SCHEMA.VIEWS") || 
             tableName.equalsIgnoreCase("INFORMATION_SCHEMA.ROUTINES"));

        if (!isLegacySystemTable) {
            String resolvedDb = activeDatabaseName;
            String resolvedTable = tableName;
            if (tableName.contains(".")) {
                int dotIdx = tableName.indexOf('.');
                resolvedDb = tableName.substring(0, dotIdx).trim();
                resolvedTable = tableName.substring(dotIdx + 1).trim();
            }

            if (resolvedDb != null) {
                String lowerDb = resolvedDb.toLowerCase();
                String lowerTable = resolvedTable.toLowerCase();

                if ("information_schema".equals(lowerDb) || "pocketsql".equals(lowerDb) || "sys".equals(lowerDb)) {
                    return systemDbManager.getSystemTable(this, lowerDb, lowerTable);
                }

                // Cross-database query for user databases (e.g., SELECT * FROM school.students)
                if (tableName.contains(".")) {
                    String targetDb = resolveDatabaseName(resolvedDb);
                    if (targetDb == null || !storageEngine.databaseExists(targetDb)) {
                        throw new Exception("Unknown database '" + resolvedDb + "'");
                    }
                    JSONObject targetSchema = storageEngine.readSchema(targetDb);
                    // Case-insensitive table name resolution in target database
                    String actualTableName = null;
                    Iterator<String> keys = targetSchema.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (key.equalsIgnoreCase(resolvedTable)) {
                            actualTableName = key;
                            break;
                        }
                    }
                    if (actualTableName == null) {
                        throw new Exception("Table '" + targetDb + "." + resolvedTable + "' doesn't exist");
                    }

                    JSONObject ts = targetSchema.getJSONObject(actualTableName);

                    // Handle views in the target database
                    if (ts.optBoolean("is_view", false)) {
                        String savedDb = activeDatabaseName;
                        JSONObject savedSchema = activeSchemaJson;
                        try {
                            activeDatabaseName = targetDb;
                            activeSchemaJson = targetSchema;
                            String viewQuery = ts.getString("query");
                            QueryResult result = execute(viewQuery);
                            if (!result.success) {
                                throw new Exception("Error evaluating view '" + actualTableName + "': " + result.message);
                            }
                            List<String> cols = new ArrayList<>();
                            for (String col : result.columns) {
                                cols.add(getDisplayColumnName(col));
                            }
                            TableData td = new TableData(actualTableName, cols, result.columnTypes);
                            for (Map<String, Object> r : result.rows) {
                                Map<String, Object> rowCopy = new HashMap<>();
                                for (int i = 0; i < cols.size(); i++) {
                                    rowCopy.put(cols.get(i), r.get(result.columns.get(i)));
                                }
                                td.rows.add(rowCopy);
                            }
                            return td;
                        } finally {
                            activeDatabaseName = savedDb;
                            activeSchemaJson = savedSchema;
                        }
                    }

                    JSONArray colArr = ts.getJSONArray("columns");
                    JSONArray typArr = ts.getJSONArray("types");
                    List<String> cols = new ArrayList<>();
                    List<String> typs = new ArrayList<>();
                    for (int i = 0; i < colArr.length(); i++) {
                        cols.add(colArr.getString(i));
                        typs.add(typArr.getString(i));
                    }
                    TableData td = new TableData(actualTableName, cols, typs);
                    JSONArray rowsArr = storageEngine.readTableRows(targetDb, actualTableName);
                    td.loadFromJSON(rowsArr);
                    return td;
                }
            }
        }

        ensureActiveSchema();
        tableName = resolveTableName(tableName);
        if ("INFORMATION_SCHEMA.STATISTICS".equalsIgnoreCase(tableName)) {
            List<String> cols = new ArrayList<>();
            cols.add("TABLE_CATALOG");
            cols.add("TABLE_SCHEMA");
            cols.add("TABLE_NAME");
            cols.add("NON_UNIQUE");
            cols.add("INDEX_SCHEMA");
            cols.add("INDEX_NAME");
            cols.add("SEQ_IN_INDEX");
            cols.add("COLUMN_NAME");
            cols.add("COLLATION");
            cols.add("CARDINALITY");
            cols.add("SUB_PART");
            cols.add("PACKED");
            cols.add("NULLABLE");
            cols.add("INDEX_TYPE");
            cols.add("COMMENT");
            cols.add("INDEX_COMMENT");
            cols.add("IS_VISIBLE");
            cols.add("EXPRESSION");

            List<String> typs = new ArrayList<>();
            typs.add("VARCHAR"); // TABLE_CATALOG
            typs.add("VARCHAR"); // TABLE_SCHEMA
            typs.add("VARCHAR"); // TABLE_NAME
            typs.add("BIGINT");  // NON_UNIQUE
            typs.add("VARCHAR"); // INDEX_SCHEMA
            typs.add("VARCHAR"); // INDEX_NAME
            typs.add("BIGINT");  // SEQ_IN_INDEX
            typs.add("VARCHAR"); // COLUMN_NAME
            typs.add("VARCHAR"); // COLLATION
            typs.add("BIGINT");  // CARDINALITY
            typs.add("BIGINT");  // SUB_PART
            typs.add("VARCHAR"); // PACKED
            typs.add("VARCHAR"); // NULLABLE
            typs.add("VARCHAR"); // INDEX_TYPE
            typs.add("VARCHAR"); // COMMENT
            typs.add("VARCHAR"); // INDEX_COMMENT
            typs.add("VARCHAR"); // IS_VISIBLE
            typs.add("VARCHAR"); // EXPRESSION

            TableData td = new TableData(tableName, cols, typs);
            if (activeSchemaJson != null) {
                List<String> tblNames = new ArrayList<>();
                Iterator<String> it = activeSchemaJson.keys();
                while (it.hasNext()) {
                    tblNames.add(it.next());
                }
                Collections.sort(tblNames);

                for (String tblName : tblNames) {
                    if (tblName.startsWith("__") || tblName.equals("INFORMATION_SCHEMA")) continue;
                    JSONObject ts = activeSchemaJson.optJSONObject(tblName);
                    if (ts == null || ts.optBoolean("is_view", false)) continue;

                    JSONObject indexesObj = ts.optJSONObject("indexes");
                    if (indexesObj != null) {
                        List<String> idxNames = new ArrayList<>();
                        Iterator<String> it2 = indexesObj.keys();
                        while (it2.hasNext()) {
                            idxNames.add(it2.next());
                        }
                        Collections.sort(idxNames, new Comparator<String>() {
                            @Override
                            public int compare(String a, String b) {
                                if ("PRIMARY".equalsIgnoreCase(a)) return -1;
                                if ("PRIMARY".equalsIgnoreCase(b)) return 1;
                                return a.compareToIgnoreCase(b);
                            }
                        });

                        for (String idxName : idxNames) {
                            JSONObject idxMeta = indexesObj.optJSONObject(idxName);
                            if (idxMeta == null) continue;

                            JSONArray columnsArr = idxMeta.optJSONArray("columns");
                            if (columnsArr == null) continue;

                            boolean unique = idxMeta.optBoolean("unique", false);
                            long nonUnique = unique ? 0L : 1L;
                            String idxType = idxMeta.optString("type", "BTREE");

                            for (int i = 0; i < columnsArr.length(); i++) {
                                String col = columnsArr.getString(i);
                                Map<String, Object> row = new HashMap<>();
                                row.put("TABLE_CATALOG", "def");
                                row.put("TABLE_SCHEMA", activeDatabaseName != null ? activeDatabaseName : "ecommerce");
                                row.put("TABLE_NAME", tblName);
                                row.put("NON_UNIQUE", nonUnique);
                                row.put("INDEX_SCHEMA", activeDatabaseName != null ? activeDatabaseName : "ecommerce");
                                row.put("INDEX_NAME", idxName);
                                row.put("SEQ_IN_INDEX", (long) (i + 1));
                                row.put("COLUMN_NAME", col);
                                row.put("COLLATION", "A");
                                row.put("CARDINALITY", 0L);
                                row.put("SUB_PART", null);
                                row.put("PACKED", null);

                                boolean nullable = true;
                                JSONObject nullables = ts.optJSONObject("nullables");
                                if (nullables != null && nullables.has(col)) {
                                    nullable = nullables.optBoolean(col, true);
                                }
                                row.put("NULLABLE", nullable ? "YES" : "");
                                row.put("INDEX_TYPE", idxType);
                                row.put("COMMENT", "");
                                row.put("INDEX_COMMENT", "");
                                row.put("IS_VISIBLE", "YES");
                                row.put("EXPRESSION", null);

                                td.rows.add(row);
                            }
                        }
                    }
                }
            }
            return td;
        }

        if ("INFORMATION_SCHEMA.VIEWS".equalsIgnoreCase(tableName)) {
            List<String> cols = new ArrayList<>();
            cols.add("TABLE_NAME");
            cols.add("TABLE_SCHEMA");
            cols.add("VIEW_DEFINITION");
            
            List<String> typs = new ArrayList<>();
            typs.add("VARCHAR");
            typs.add("VARCHAR");
            typs.add("LONGTEXT");
            
            TableData td = new TableData(tableName, cols, typs);
            if (activeSchemaJson != null) {
                Iterator<String> keys = activeSchemaJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject ts = activeSchemaJson.optJSONObject(key);
                    if (ts != null && ts.optBoolean("is_view", false)) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("TABLE_NAME", key);
                        row.put("TABLE_SCHEMA", activeDatabaseName != null ? activeDatabaseName : "ecommerce");
                        row.put("VIEW_DEFINITION", SqlFormatter.formatSql(ts.optString("query", "")));
                        td.rows.add(row);
                    }
                }
            }
            return td;
        }

        if ("INFORMATION_SCHEMA.ROUTINES".equalsIgnoreCase(tableName)) {
            List<String> cols = new ArrayList<>();
            cols.add("SPECIFIC_NAME");
            cols.add("ROUTINE_CATALOG");
            cols.add("ROUTINE_SCHEMA");
            cols.add("ROUTINE_NAME");
            cols.add("ROUTINE_TYPE");
            cols.add("DATA_TYPE");
            cols.add("CHARACTER_MAXIMUM_LENGTH");
            cols.add("CHARACTER_OCTET_LENGTH");
            cols.add("NUMERIC_PRECISION");
            cols.add("NUMERIC_SCALE");
            cols.add("DATETIME_PRECISION");
            cols.add("CHARACTER_SET_NAME");
            cols.add("COLLATION_NAME");
            cols.add("DTD_IDENTIFIER");
            cols.add("ROUTINE_BODY");
            cols.add("ROUTINE_DEFINITION");
            cols.add("EXTERNAL_NAME");
            cols.add("EXTERNAL_LANGUAGE");
            cols.add("PARAMETER_STYLE");
            cols.add("IS_DETERMINISTIC");
            cols.add("SQL_DATA_ACCESS");
            cols.add("SQL_PATH");
            cols.add("SECURITY_TYPE");
            cols.add("CREATED");
            cols.add("LAST_ALTERED");
            cols.add("SQL_MODE");
            cols.add("ROUTINE_COMMENT");
            cols.add("DEFINER");
            cols.add("CHARACTER_SET_CLIENT");
            cols.add("COLLATION_CONNECTION");
            cols.add("DATABASE_COLLATION");

            List<String> typs = new ArrayList<>();
            typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");
            typs.add("VARCHAR");typs.add("BIGINT");typs.add("BIGINT");typs.add("BIGINT");typs.add("BIGINT");
            typs.add("BIGINT");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("LONGTEXT");typs.add("VARCHAR");
            typs.add("LONGTEXT");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");
            typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("DATETIME");typs.add("DATETIME");
            typs.add("VARCHAR");typs.add("TEXT");typs.add("VARCHAR");typs.add("VARCHAR");typs.add("VARCHAR");
            typs.add("VARCHAR");

            TableData td = new TableData(tableName, cols, typs);
            if (activeSchemaJson != null) {
                String defaultCreated = "2026-08-30 00:00:00";
                // Add procedures
                JSONObject procs = activeSchemaJson.optJSONObject("__procedures__");
                if (procs != null) {
                    Iterator<String> keys = procs.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        JSONObject procObj = procs.optJSONObject(key);
                        String definition = procObj != null ? procObj.optString("definition", procObj.optString("body", "")) : "";
                        String created = procObj != null ? procObj.optString("created", defaultCreated) : defaultCreated;
                        String lastAltered = procObj != null ? procObj.optString("last_altered", created) : created;

                        Map<String, Object> row = new HashMap<>();
                        row.put("SPECIFIC_NAME", key);
                        row.put("ROUTINE_CATALOG", "def");
                        row.put("ROUTINE_SCHEMA", activeDatabaseName != null ? activeDatabaseName : "ecommerce");
                        row.put("ROUTINE_NAME", key);
                        row.put("ROUTINE_TYPE", "PROCEDURE");
                        row.put("DATA_TYPE", null);
                        row.put("CHARACTER_MAXIMUM_LENGTH", null);
                        row.put("CHARACTER_OCTET_LENGTH", null);
                        row.put("NUMERIC_PRECISION", null);
                        row.put("NUMERIC_SCALE", null);
                        row.put("DATETIME_PRECISION", null);
                        row.put("CHARACTER_SET_NAME", "utf8mb4");
                        row.put("COLLATION_NAME", "utf8mb4_general_ci");
                        row.put("DTD_IDENTIFIER", null);
                        row.put("ROUTINE_BODY", "SQL");
                        row.put("ROUTINE_DEFINITION", SqlFormatter.formatSql(definition));
                        row.put("EXTERNAL_NAME", null);
                        row.put("EXTERNAL_LANGUAGE", "SQL");
                        row.put("PARAMETER_STYLE", "SQL");
                        row.put("IS_DETERMINISTIC", "NO");
                        row.put("SQL_DATA_ACCESS", "CONTAINS SQL");
                        row.put("SQL_PATH", null);
                        row.put("SECURITY_TYPE", "DEFINER");
                        row.put("CREATED", created);
                        row.put("LAST_ALTERED", lastAltered);
                        row.put("SQL_MODE", "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
                        row.put("ROUTINE_COMMENT", "");
                        row.put("DEFINER", currentUser != null ? currentUser + "@localhost" : "root@localhost");
                        row.put("CHARACTER_SET_CLIENT", "utf8mb4");
                        row.put("COLLATION_CONNECTION", "utf8mb4_general_ci");
                        row.put("DATABASE_COLLATION", "utf8mb4_general_ci");
                        td.rows.add(row);
                    }
                }

                // Add functions
                JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
                if (fns != null) {
                    Iterator<String> keys = fns.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        JSONObject funcObj = fns.optJSONObject(key);
                        String returnType = funcObj != null ? funcObj.optString("returnType", "") : "";
                        String definition = "";
                        if (funcObj != null) {
                            JSONArray bodyArr = funcObj.optJSONArray("body");
                            if (bodyArr != null) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < bodyArr.length(); i++) {
                                    JSONObject tok = bodyArr.optJSONObject(i);
                                    if (tok != null) {
                                        sb.append(tok.optString("value", "")).append(" ");
                                    }
                                }
                                definition = sb.toString().trim();
                            } else {
                                definition = funcObj.optString("definition", "");
                            }
                        }
                        String created = funcObj != null ? funcObj.optString("created", defaultCreated) : defaultCreated;
                        String lastAltered = funcObj != null ? funcObj.optString("last_altered", created) : created;

                        Map<String, Object> row = new HashMap<>();
                        row.put("SPECIFIC_NAME", key);
                        row.put("ROUTINE_CATALOG", "def");
                        row.put("ROUTINE_SCHEMA", activeDatabaseName != null ? activeDatabaseName : "ecommerce");
                        row.put("ROUTINE_NAME", key);
                        row.put("ROUTINE_TYPE", "FUNCTION");
                        row.put("DATA_TYPE", returnType);
                        row.put("CHARACTER_MAXIMUM_LENGTH", null);
                        row.put("CHARACTER_OCTET_LENGTH", null);
                        row.put("NUMERIC_PRECISION", null);
                        row.put("NUMERIC_SCALE", null);
                        row.put("DATETIME_PRECISION", null);
                        row.put("CHARACTER_SET_NAME", "utf8mb4");
                        row.put("COLLATION_NAME", "utf8mb4_general_ci");
                        row.put("DTD_IDENTIFIER", returnType);
                        row.put("ROUTINE_BODY", "SQL");
                        row.put("ROUTINE_DEFINITION", SqlFormatter.formatSql(definition));
                        row.put("EXTERNAL_NAME", null);
                        row.put("EXTERNAL_LANGUAGE", "SQL");
                        row.put("PARAMETER_STYLE", "SQL");
                        row.put("IS_DETERMINISTIC", "NO");
                        row.put("SQL_DATA_ACCESS", "CONTAINS SQL");
                        row.put("SQL_PATH", null);
                        row.put("SECURITY_TYPE", "DEFINER");
                        row.put("CREATED", created);
                        row.put("LAST_ALTERED", lastAltered);
                        row.put("SQL_MODE", "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
                        row.put("ROUTINE_COMMENT", "");
                        row.put("DEFINER", currentUser != null ? currentUser + "@localhost" : "root@localhost");
                        row.put("CHARACTER_SET_CLIENT", "utf8mb4");
                        row.put("COLLATION_CONNECTION", "utf8mb4_general_ci");
                        row.put("DATABASE_COLLATION", "utf8mb4_general_ci");
                        td.rows.add(row);
                    }
                }
            }
            return td;
        }

        if ("sqlite_master".equalsIgnoreCase(tableName) || "sqlite_schema".equalsIgnoreCase(tableName)) {
            List<String> cols = java.util.Arrays.asList("type", "name", "tbl_name", "rootpage", "sql");
            List<String> typs = java.util.Arrays.asList("TEXT", "TEXT", "TEXT", "INTEGER", "TEXT");
            TableData td = new TableData(tableName, cols, typs);

            if (activeSchemaJson != null) {
                Iterator<String> tblKeys = activeSchemaJson.keys();
                while (tblKeys.hasNext()) {
                    String tblName = tblKeys.next();
                    if (tblName.startsWith("__")) continue;
                    JSONObject tblObj = activeSchemaJson.optJSONObject(tblName);
                    if (tblObj == null) continue;
                    boolean isView = tblObj.optBoolean("is_view", false);

                    StringBuilder sqlSb = new StringBuilder();
                    if (isView) {
                        sqlSb.append("CREATE VIEW ").append(tblName).append(" AS ").append(tblObj.optString("query", ""));
                    } else {
                        sqlSb.append("CREATE TABLE ").append(tblName).append(" (");
                        JSONArray cArr = tblObj.optJSONArray("columns");
                        JSONArray tArr = tblObj.optJSONArray("types");
                        if (cArr != null && tArr != null) {
                            for (int i = 0; i < cArr.length(); i++) {
                                if (i > 0) sqlSb.append(", ");
                                sqlSb.append(cArr.optString(i)).append(" ").append(tArr.optString(i));
                            }
                        }
                        sqlSb.append(")");
                    }

                    Map<String, Object> row = new HashMap<>();
                    row.put("type", isView ? "view" : "table");
                    row.put("name", tblName);
                    row.put("tbl_name", tblName);
                    row.put("rootpage", 0L);
                    row.put("sql", sqlSb.toString());
                    td.rows.add(row);
                }

                // Add triggers
                JSONObject triggers = activeSchemaJson.optJSONObject("__triggers__");
                if (triggers != null) {
                    Iterator<String> trgKeys = triggers.keys();
                    while (trgKeys.hasNext()) {
                        String trgName = trgKeys.next();
                        JSONObject trgObj = triggers.optJSONObject(trgName);
                        Map<String, Object> row = new HashMap<>();
                        row.put("type", "trigger");
                        row.put("name", trgName);
                        row.put("tbl_name", trgObj != null ? trgObj.optString("table", "") : "");
                        row.put("rootpage", 0L);
                        row.put("sql", trgObj != null ? trgObj.optString("definition", "") : "");
                        td.rows.add(row);
                    }
                }
            }
            return td;
        }

        if (tableCache.containsKey(tableName)) {
            return tableCache.get(tableName);
        }

        if (!activeSchemaJson.has(tableName)) {
            throw new Exception("Table '" + tableName + "' does not exist");
        }

        JSONObject ts = activeSchemaJson.getJSONObject(tableName);
        if (ts.optBoolean("is_view", false)) {
            String viewQuery = ts.getString("query");
            QueryResult result = execute(viewQuery);
            if (!result.success) {
                throw new Exception("Error evaluating view '" + tableName + "': " + result.message);
            }
            List<String> cols = new ArrayList<>();
            for (String col : result.columns) {
                cols.add(getDisplayColumnName(col));
            }
            List<String> typs = result.columnTypes;
            TableData td = new TableData(tableName, cols, typs);
            for (Map<String, Object> r : result.rows) {
                Map<String, Object> rowCopy = new HashMap<>();
                for (int i = 0; i < cols.size(); i++) {
                    rowCopy.put(cols.get(i), r.get(result.columns.get(i)));
                }
                td.rows.add(rowCopy);
            }
            return td;
        }

        JSONArray colArr = ts.getJSONArray("columns");
        JSONArray typArr = ts.getJSONArray("types");

        List<String> cols = new ArrayList<>();
        List<String> typs = new ArrayList<>();
        for (int i = 0; i < colArr.length(); i++) {
            cols.add(colArr.getString(i));
            typs.add(typArr.getString(i));
        }

        TableData td = new TableData(tableName, cols, typs);
        JSONArray rowsArr = storageEngine.readTableRows(activeDatabaseName, tableName);
        td.loadFromJSON(rowsArr);

        tableCache.put(tableName, td);
        return td;
    }

    private Object validateAndConvertType(String colName, Object val, String rawType) throws Exception {
        return SqlDataType.validateAndConvertType(colName, val, rawType);
    }

    private Object getDefaultValue(String tableName, String colName) throws Exception {
        ensureActiveSchema();
        JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
        return SqlDefaults.getDefaultValue(tableSchema, colName);
    }

    private boolean isAutoIncrementColumn(String tableName, String colName) throws Exception {
        ensureActiveSchema();
        JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
        if (tableSchema == null) return false;

        JSONObject extrasObj = tableSchema.optJSONObject("extras");
        if (extrasObj != null && extrasObj.has(colName)) {
            String extra = extrasObj.getString(colName);
            return "auto_increment".equalsIgnoreCase(extra);
        }
        return false;
    }

    private long getNextAutoIncrementValue(TableData td, String colName) {
        long schemaNext = 1;
        if (activeSchemaJson != null && activeSchemaJson.has(td.tableName)) {
            JSONObject tblSchema = activeSchemaJson.optJSONObject(td.tableName);
            if (tblSchema != null) {
                schemaNext = tblSchema.optLong("auto_increment", tblSchema.optLong("AUTO_INCREMENT", 1L));
            }
        }
        Long cachedMax = td.autoIncrementCounters.get(colName);
        long maxFromRows = 0;
        if (cachedMax != null) {
            maxFromRows = cachedMax;
        } else {
            for (Map<String, Object> r : td.rows) {
                Object v = r.get(colName);
                if (v != null) {
                    try {
                        long lv = Long.parseLong(String.valueOf(v));
                        if (lv > maxFromRows) {
                            maxFromRows = lv;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        
        long nextVal = Math.max(schemaNext, maxFromRows + 1);
        td.autoIncrementCounters.put(colName, nextVal);
        
        if (activeSchemaJson != null && activeSchemaJson.has(td.tableName)) {
            JSONObject tblSchema = activeSchemaJson.optJSONObject(td.tableName);
            if (tblSchema != null) {
                try {
                    tblSchema.put("auto_increment", nextVal + 1);
                    storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);
                } catch (Exception ignored) {}
            }
        }
        
        return nextVal;
    }

    boolean isColumnVisible(String tableName, String colName) {
        if (activeSchemaJson == null || tableName == null) return true;
        JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
        if (tableSchema == null) return true;
        JSONObject attrsObj = tableSchema.optJSONObject("attributes");
        if (attrsObj == null) return true;
        JSONObject colAttrs = attrsObj.optJSONObject(colName);
        if (colAttrs == null) return true;
        return colAttrs.optBoolean("visible", true);
    }

    boolean isColumnNullable(JSONObject tableSchema, String colName) {
        JSONObject nullablesObj = tableSchema.optJSONObject("nullables");
        if (nullablesObj != null && nullablesObj.has(colName)) {
            return nullablesObj.optBoolean(colName, true);
        }
        return true;
    }

    String getColumnKeyType(JSONObject tableSchema, String colName) {
        JSONObject keysObj = tableSchema.optJSONObject("keys");
        if (keysObj != null && keysObj.has(colName)) {
            return keysObj.optString(colName, "");
        }
        return "";
    }

    private String getColumnExtraInfo(JSONObject tableSchema, String colName) {
        JSONObject extrasObj = tableSchema.optJSONObject("extras");
        if (extrasObj != null && extrasObj.has(colName)) {
            return extrasObj.optString(colName, "");
        }
        return "";
    }

    private void validateRowConstraints(String tableName, Map<String, Object> row, TableData td, Map<String, Object> originalRow) throws Exception {
        ensureActiveSchema();
        JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
        if (tableSchema == null) return;

        // 1. Evaluate generated columns
        JSONObject attrsObj = tableSchema.optJSONObject("attributes");
        if (attrsObj != null) {
            for (String colName : td.columns) {
                JSONObject colAttrsJson = attrsObj.optJSONObject(colName);
                if (colAttrsJson != null) {
                    SqlAttributes attrs = SqlAttributes.fromJsonObject(colAttrsJson);
                    if (attrs.generatedExpr != null) {
                        Object genVal = SqlFunctions.evaluate(attrs.generatedExpr, row, this);
                        row.put(colName, genVal);
                    }
                }
            }
        }

        // 2. Validate UNSIGNED and other attributes
        if (attrsObj != null) {
            for (String colName : td.columns) {
                JSONObject colAttrsJson = attrsObj.optJSONObject(colName);
                if (colAttrsJson != null) {
                    SqlAttributes attrs = SqlAttributes.fromJsonObject(colAttrsJson);
                    Object val = row.get(colName);
                    SqlAttributes.validateValue(colName, val, attrs);
                }
            }
        }

        // 3. Format ZEROFILL and other attributes
        if (attrsObj != null) {
            for (int i = 0; i < td.columns.size(); i++) {
                String colName = td.columns.get(i);
                JSONObject colAttrsJson = attrsObj.optJSONObject(colName);
                if (colAttrsJson != null) {
                    SqlAttributes attrs = SqlAttributes.fromJsonObject(colAttrsJson);
                    Object val = row.get(colName);
                    String colType = td.types.get(i);
                    Object formatted = SqlAttributes.formatValue(val, attrs, colType);
                    row.put(colName, formatted);
                }
            }
        }

        SqlConstraint.validateRow(tableName, row, td, originalRow, tableSchema, this);
    }

    private boolean compareValuesEqual(Object v1, Object v2) {
        if (v1 == null || v2 == null) return false;
        if (v1 instanceof Number && v2 instanceof Number) {
            return ((Number) v1).doubleValue() == ((Number) v2).doubleValue();
        }
        return v1.toString().trim().equalsIgnoreCase(v2.toString().trim());
    }

    private int compareValuesForSort(Object v1, Object v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        if (v1 instanceof Number && v2 instanceof Number) {
            return Double.compare(((Number) v1).doubleValue(), ((Number) v2).doubleValue());
        }
        try {
            double d1 = Double.parseDouble(v1.toString());
            double d2 = Double.parseDouble(v2.toString());
            return Double.compare(d1, d2);
        } catch (Exception e) {
            return SqlCollation.compare(v1.toString(), v2.toString(), "utf8mb4_general_ci");
        }
    }

    public QueryResult insertInto(String tableName, List<String> colNames, List<List<Object>> valuesList) throws Exception {
        return insertInto(tableName, colNames, valuesList, null, false);
    }

    public QueryResult insertInto(String tableName, List<String> colNames, List<List<Object>> valuesList, Map<String, String> updateAssignments) throws Exception {
        return insertInto(tableName, colNames, valuesList, updateAssignments, false);
    }

    public QueryResult insertInto(String tableName, List<String> colNames, List<List<Object>> valuesList, Map<String, String> updateAssignments, boolean ignore) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("INSERT", activeDatabaseName, tableName);
        TableData td = getOrLoadTable(tableName);

        int affected = 0;
        for (List<Object> values : valuesList) {
            Map<String, Object> newRow = new HashMap<>();

            try {
                if (colNames == null) {
                    // Positional insert
                    if (values.isEmpty()) {
                        // All default values
                        for (int i = 0; i < td.columns.size(); i++) {
                            String col = td.columns.get(i);
                            Object val = getDefaultValue(tableName, col);
                            if (val == null && isAutoIncrementColumn(tableName, col)) {
                                val = getNextAutoIncrementValue(td, col);
                            }
                            Object finalVal = validateAndConvertType(col, val, td.types.get(i));
                            newRow.put(col, finalVal);
                        }
                    } else {
                        if (values.size() != td.columns.size()) {
                            throw new Exception("Column count doesn't match value count. Table has " + td.columns.size() + " columns, but " + values.size() + " values were supplied.");
                        }
                        for (int i = 0; i < td.columns.size(); i++) {
                            String col = td.columns.get(i);
                            String type = td.types.get(i);
                            Object val = values.get(i);
                            if (val instanceof String && ((String) val).startsWith("\u0000EXPR\u0000")) {
                                String exprStr = ((String) val).substring("\u0000EXPR\u0000".length());
                                val = SqlFunctions.evaluate(exprStr, newRow, this);
                            }
                            if ("\u0000DEFAULT\u0000".equals(val)) {
                                val = getDefaultValue(tableName, col);
                            }
                            if (val == null && isAutoIncrementColumn(tableName, col)) {
                                val = getNextAutoIncrementValue(td, col);
                            }
                            Object finalVal = validateAndConvertType(col, val, type);
                            newRow.put(col, finalVal);
                        }
                    }
                } else {
                    // Column-specific insert
                    if (values.size() != colNames.size()) {
                        throw new Exception("Column count doesn't match value count in insert list.");
                    }
                    
                    // Map of name -> index
                    Map<String, Object> insertMap = new HashMap<>();
                    for (int i = 0; i < colNames.size(); i++) {
                        String col = colNames.get(i);
                        String actualColName = null;
                        for (String c : td.columns) {
                            if (c.equalsIgnoreCase(col)) {
                                actualColName = c;
                                break;
                            }
                        }
                        if (actualColName == null) {
                            throw new Exception("Error: Unknown column '" + col + "' in table '" + tableName + "'");
                        }
                        Object val = values.get(i);
                        if (val instanceof String && ((String) val).startsWith("\u0000EXPR\u0000")) {
                            String exprStr = ((String) val).substring("\u0000EXPR\u0000".length());
                            val = SqlFunctions.evaluate(exprStr, insertMap, this);
                        }
                        if ("\u0000DEFAULT\u0000".equals(val)) {
                            val = getDefaultValue(tableName, actualColName);
                        }
                        insertMap.put(actualColName, val);
                    }

                    // Fill all schema columns
                    for (int i = 0; i < td.columns.size(); i++) {
                        String col = td.columns.get(i);
                        String type = td.types.get(i);
                        Object val;
                        if (insertMap.containsKey(col)) {
                            val = insertMap.get(col);
                        } else {
                            // Column was not supplied, use default value!
                            val = getDefaultValue(tableName, col);
                        }
                        if (val == null && isAutoIncrementColumn(tableName, col)) {
                            val = getNextAutoIncrementValue(td, col);
                        }
                        Object finalVal = validateAndConvertType(col, val, type);
                        newRow.put(col, finalVal);
                    }
                }

                Map<String, Object> conflictingRow = findConflictingRow(tableName, newRow, td);
                if (conflictingRow != null && updateAssignments != null && !updateAssignments.isEmpty()) {
                    // Perform UPDATE in-place
                    Map<String, Object> updatedRow = new HashMap<>(conflictingRow);
                    for (Map.Entry<String, String> entry : updateAssignments.entrySet()) {
                        String col = entry.getKey();
                        String exprStr = entry.getValue();
                        Object val = evaluateDuplicateKeyExpr(exprStr, conflictingRow, newRow);
                        
                        // Validate and convert type
                        int colIdx = td.columns.indexOf(col);
                        if (colIdx != -1) {
                            val = validateAndConvertType(col, val, td.types.get(colIdx));
                        }
                        updatedRow.put(col, val);
                    }
                    
                    // Validate constraints
                    validateRowConstraints(tableName, updatedRow, td, conflictingRow);
                    
                    // Update in-place
                    conflictingRow.putAll(updatedRow);
                    affected += 2;
                } else if (conflictingRow != null && ignore) {
                    // Skip duplicate row on IGNORE
                    continue;
                } else {
                    validateRowConstraints(tableName, newRow, td, null);
                    td.rows.add(newRow);
                    affected++;
                }
            } catch (Exception ex) {
                if (ignore) {
                    continue;
                }
                throw ex;
            }
        }

        td.isDirty = true;
        if (!deferWrite) {
            storageEngine.writeTableRows(activeDatabaseName, tableName, td.toJSONArray());
            td.isDirty = false;
        }

        return QueryResult.createSuccess("Query OK, " + affected + " row" + (affected == 1 ? "" : "s") + " affected", affected, 0);
    }

    private Map<String, Object> findConflictingRow(String tableName, Map<String, Object> newRow, TableData td) throws Exception {
        ensureActiveSchema();
        JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
        if (tableSchema == null) return null;

        // 1. Check Primary Key conflict
        JSONArray pkArr = tableSchema.optJSONArray("primary_key");
        List<String> pkCols = new ArrayList<>();
        if (pkArr != null) {
            for (int i = 0; i < pkArr.length(); i++) {
                pkCols.add(pkArr.getString(i));
            }
        }
        if (!pkCols.isEmpty()) {
            for (Map<String, Object> existing : td.rows) {
                boolean match = true;
                for (String col : pkCols) {
                    if (!compareValuesEqual(newRow.get(col), existing.get(col))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return existing;
                }
            }
        }

        // 2. Check Unique Key conflicts
        JSONArray uniques = tableSchema.optJSONArray("uniques");
        if (uniques != null) {
            for (int i = 0; i < uniques.length(); i++) {
                JSONArray group = uniques.getJSONArray(i);
                if (group.length() == 0) continue;
                List<String> uniqueCols = new ArrayList<>();
                for (int j = 0; j < group.length(); j++) {
                    uniqueCols.add(group.getString(j));
                }
                
                for (Map<String, Object> existing : td.rows) {
                    boolean match = true;
                    for (String col : uniqueCols) {
                        if (!compareValuesEqual(newRow.get(col), existing.get(col))) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        return existing;
                    }
                }
            }
        }
        
        JSONObject indexesObj = tableSchema.optJSONObject("indexes");
        if (indexesObj != null) {
            Iterator<String> keys = indexesObj.keys();
            while (keys.hasNext()) {
                String idxName = keys.next();
                JSONObject idxMeta = indexesObj.optJSONObject(idxName);
                if (idxMeta != null && idxMeta.optBoolean("unique", false)) {
                    JSONArray group = idxMeta.optJSONArray("columns");
                    if (group != null && group.length() > 0) {
                        List<String> uniqueCols = new ArrayList<>();
                        for (int j = 0; j < group.length(); j++) {
                            uniqueCols.add(group.getString(j));
                        }
                        
                        for (Map<String, Object> existing : td.rows) {
                            boolean match = true;
                            for (String col : uniqueCols) {
                                if (!compareValuesEqual(newRow.get(col), existing.get(col))) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match) {
                                return existing;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private Object evaluateDuplicateKeyExpr(String exprStr, Map<String, Object> conflictingRow, Map<String, Object> newRow) throws Exception {
        String processed = exprStr;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)VALUES\\s*\\(\\s*(\\w+)\\s*\\)");
        java.util.regex.Matcher m = p.matcher(processed);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String colName = m.group(1);
            Object insertVal = newRow.get(colName);
            String replacement;
            if (insertVal == null) {
                replacement = "NULL";
            } else if (insertVal instanceof String) {
                replacement = "'" + ((String) insertVal).replace("'", "''") + "'";
            } else {
                replacement = insertVal.toString();
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        processed = sb.toString();
        
        return SqlFunctions.evaluate(processed, conflictingRow, this);
    }

    @SuppressWarnings("unchecked")
    public static Object getRowValue(Map<String, Object> row, String colName) {
        if (colName == null || row == null) return null;
        if (row.containsKey(colName)) {
            return row.get(colName);
        }

        // Check if cache exists or can be built
        Map<String, String> cache = (Map<String, String>) row.get("\u0000cache\u0000");
        if (cache == null) {
            cache = new HashMap<>();
            for (String key : row.keySet()) {
                if (key.startsWith("\u0000")) continue;
                String lowerKey = key.toLowerCase();
                cache.put(lowerKey, key);
                
                // Add bare name
                int dotIdx = key.indexOf('.');
                if (dotIdx != -1) {
                    String bare = key.substring(dotIdx + 1).toLowerCase();
                    if (!cache.containsKey(bare)) {
                        cache.put(bare, key);
                    }
                    String dotSuffix = key.substring(dotIdx).toLowerCase();
                    if (!cache.containsKey(dotSuffix)) {
                        cache.put(dotSuffix, key);
                    }
                } else {
                    String dotSuffix = "." + lowerKey;
                    if (!cache.containsKey(dotSuffix)) {
                        cache.put(dotSuffix, key);
                    }
                }
            }
            try {
                row.put("\u0000cache\u0000", cache);
            } catch (UnsupportedOperationException ignored) {}
        }

        String lowerCol = colName.toLowerCase();
        String targetKey = cache.get(lowerCol);
        if (targetKey != null) {
            return row.get(targetKey);
        }

        int dotIdx = colName.indexOf('.');
        if (dotIdx != -1) {
            String bare = colName.substring(dotIdx + 1).toLowerCase();
            targetKey = cache.get(bare);
            if (targetKey != null) {
                return row.get(targetKey);
            }
            
            String qual = colName.substring(0, dotIdx).toLowerCase();
            for (String key : row.keySet()) {
                if (key.startsWith("\u0000")) continue;
                int kDot = key.indexOf('.');
                if (kDot != -1) {
                    String kQual = key.substring(0, kDot).toLowerCase();
                    String kBare = key.substring(kDot + 1).toLowerCase();
                    if (kQual.equals(qual) && kBare.equals(bare)) {
                        return row.get(key);
                    }
                }
            }
        } else {
            String dotSuffix = "." + lowerCol;
            targetKey = cache.get(dotSuffix);
            if (targetKey != null) {
                return row.get(targetKey);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Object getRowValueWithAlias(Map<String, Object> row, String colName, Map<String, String> aliases) {
        if (colName == null) return null;
        Object val = getRowValue(row, colName);
        if (val != null) return val;

        if (aliases != null && !aliases.isEmpty()) {
            String colLower = colName.toLowerCase();
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (entry.getKey().startsWith("\u0000")) continue;
                if (entry.getValue() != null && entry.getValue().toLowerCase().equals(colLower)) {
                    val = getRowValue(row, entry.getKey());
                    if (val != null) return val;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean rowHasColumn(Map<String, Object> row, String col) {
        if (row == null || col == null) return false;
        if (getRowValue(row, col) != null) return true;
        Map<String, String> cache = (Map<String, String>) row.get("\u0000cache\u0000");
        if (cache != null) {
            String lower = col.toLowerCase();
            if (cache.containsKey(lower)) return true;
            int dotIdx = col.indexOf('.');
            if (dotIdx != -1) {
                String bare = col.substring(dotIdx + 1).toLowerCase();
                if (cache.containsKey(bare)) return true;
            }
        }
        return false;
    }

    private boolean joinConditionMatches(Map<String, Object> leftRow, Map<String, Object> rightRow, Clause.Join join) {
        Object leftVal = getRowValue(leftRow, join.leftCol);
        Object rightVal = getRowValue(rightRow, join.rightCol);
        boolean matches = false;
        
        if (leftVal != null && rightVal != null) {
            matches = compareValuesEqual(leftVal, rightVal);
        }
        
        if (!matches) {
            // Try swapped
            leftVal = getRowValue(rightRow, join.leftCol);
            rightVal = getRowValue(leftRow, join.rightCol);
            if (leftVal != null && rightVal != null) {
                matches = compareValuesEqual(leftVal, rightVal);
            }
        }
        
        if (!matches) {
            return false;
        }

        // Evaluate extra conditions!
        if (join.extraConditions != null && !join.extraConditions.isEmpty()) {
            Map<String, Object> combined = new HashMap<>();
            combined.putAll(leftRow);
            combined.putAll(rightRow);
            for (Clause.Where cond : join.extraConditions) {
                if (!cond.evaluate(combined, null, this)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getDisplayColumnName(String col) {
        if (col == null) return null;
        if (col.contains(".")) {
            String trimmed = col.trim();
            if (!trimmed.contains("(") && !trimmed.contains(")") && !trimmed.contains(" ") && !trimmed.contains("'") && !trimmed.contains("\"")) {
                return trimmed.substring(trimmed.indexOf('.') + 1);
            }
        }
        return col;
    }

    private boolean isAggregate(String expr) {
        if (expr == null) return false;
        if (expr.toUpperCase().contains("OVER")) return false;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)\\b(COUNT|SUM|AVG|MIN|MAX|GROUP_CONCAT)\\s*\\(");
        return p.matcher(expr).find();
    }

    private Object evaluateAggregate(String aggExpr, List<Map<String, Object>> groupRows) {
        if (aggExpr == null || groupRows == null || groupRows.isEmpty()) return null;

        if (aggExpr.toUpperCase().contains(" OVER ")) {
            return null;
        }

        // Use balanced-parentheses extraction instead of simple [^)]* regex
        // so that COUNT(DISTINCT FORMAT(order_date, 'yyyy-MM')) is handled correctly.
        List<int[]> aggMatches = findAggregateMatches(aggExpr);
        if (aggMatches.isEmpty()) {
            return getRowValue(groupRows.get(0), aggExpr);
        }

        // Replace each AGG(...) with its computed value, right-to-left to preserve positions
        StringBuilder result = new StringBuilder(aggExpr);
        Object singleResult = null;
        int matchCount = aggMatches.size();
        for (int i = aggMatches.size() - 1; i >= 0; i--) {
            int[] m = aggMatches.get(i);
            int start = m[0], end = m[1]; // start = index of func name start, end = index after closing ')'
            String fullMatch = aggExpr.substring(start, end);
            // Extract funcName and inner args
            int parenStart = fullMatch.indexOf('(');
            String funcName = fullMatch.substring(0, parenStart).trim().toUpperCase();
            String inner = fullMatch.substring(parenStart + 1, fullMatch.length() - 1).trim();
            Object val = evaluateSingleAggregate(funcName, inner, groupRows);
            singleResult = val;
            String rep = val == null ? "NULL" : val.toString();
            result.replace(start, end, rep);
        }

        String finalExpr = result.toString().trim();
        if ("NULL".equalsIgnoreCase(finalExpr)) {
            return null;
        }
        // If the result is purely a single aggregate result, return it directly to avoid
        // re-evaluation (e.g., "2020-01-15" being parsed as arithmetic 2020-01-15=2004)
        if (matchCount == 1 && singleResult != null && finalExpr.equals(singleResult.toString())) {
            return singleResult;
        }
        try {
            return SqlFunctions.evaluate(finalExpr, groupRows.get(0), this);
        } catch (Exception e) {
            return finalExpr;
        }
    }

    /**
     * Finds all top-level aggregate function matches (COUNT, SUM, AVG, MIN, MAX, GROUP_CONCAT) in an expression,
     * respecting nested parentheses. Returns list of [start, end) index pairs.
     */
    private List<int[]> findAggregateMatches(String expr) {
        List<int[]> results = new ArrayList<>();
        java.util.regex.Pattern funcStart = java.util.regex.Pattern.compile("(?i)\\b(COUNT|SUM|AVG|MIN|MAX|GROUP_CONCAT)\\s*\\(");
        java.util.regex.Matcher m = funcStart.matcher(expr);
        while (m.find()) {
            int start = m.start();
            if (isIndexInsideSubquery(expr, start)) {
                continue;
            }
            int parenOpen = m.end() - 1; // position of '('
            // Walk forward to find the matching closing ')'
            int depth = 1;
            int pos = parenOpen + 1;
            boolean inStr = false;
            char strChar = 0;
            while (pos < expr.length() && depth > 0) {
                char c = expr.charAt(pos);
                if (inStr) {
                    if (c == strChar) inStr = false;
                } else if (c == '\'' || c == '"') {
                    inStr = true; strChar = c;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                pos++;
            }
            if (depth == 0) {
                results.add(new int[]{start, pos}); // pos is one past the closing ')'
            }
        }
        return results;
    }

    private boolean isIndexInsideSubquery(String expr, int index) {
        int pos = 0;
        String upper = expr.toUpperCase();
        while (pos < expr.length()) {
            int subIdx = upper.indexOf("(SELECT", pos);
            if (subIdx == -1) break;
            int depth = 1;
            int cur = subIdx + 1;
            boolean inStr = false;
            char strChar = 0;
            while (cur < expr.length() && depth > 0) {
                char c = expr.charAt(cur);
                if (inStr) {
                    if (c == strChar) inStr = false;
                } else if (c == '\'' || c == '"') {
                    inStr = true; strChar = c;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                cur++;
            }
            if (index > subIdx && index < cur) {
                return true;
            }
            pos = subIdx + 7;
        }
        return false;
    }



    private Object getRowValueOrEvaluate(Map<String, Object> r, String expr) {
        return getRowValueOrEvaluateWithAlias(r, expr, null);
    }

    private Object getRowValueOrEvaluateWithAlias(Map<String, Object> r, String expr, Map<String, String> aliases) {
        if (expr == null || expr.trim().isEmpty()) return null;
        Object val = getRowValueWithAlias(r, expr, aliases);
        if (val != null) return val;

        String targetExpr = expr;
        if (aliases != null) {
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(expr)) {
                    targetExpr = entry.getKey();
                    break;
                }
            }
        }

        val = getRowValue(r, targetExpr);
        if (val == null && !r.containsKey(targetExpr)) {
            try {
                val = SqlFunctions.evaluate(targetExpr, r, this);
            } catch (Exception ignored) {}
        }
        return val;
    }

    private Object evaluateSingleAggregate(String funcName, String col, List<Map<String, Object>> groupRows) {
        // Strip DISTINCT modifier (e.g. COUNT(DISTINCT FORMAT(...)))
        boolean distinct = false;
        String colExpr = col.trim();
        if (colExpr.toUpperCase().startsWith("DISTINCT ")) {
            distinct = true;
            colExpr = colExpr.substring("DISTINCT ".length()).trim();
        } else if (colExpr.toUpperCase().startsWith("DISTINCT\t")) {
            distinct = true;
            colExpr = colExpr.substring(9).trim();
        }

        if ("COUNT".equals(funcName)) {
            if ("*".equals(colExpr)) {
                return (long) groupRows.size();
            }
            if (distinct) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Map<String, Object> r : groupRows) {
                    Object v = getRowValueOrEvaluate(r, colExpr);
                    if (v != null) seen.add(v.toString());
                }
                return (long) seen.size();
            }
            long count = 0;
            for (Map<String, Object> r : groupRows) {
                if (getRowValueOrEvaluate(r, colExpr) != null) {
                    count++;
                }
            }
            return count;
        }

        if ("SUM".equals(funcName)) {
            double sum = 0;
            boolean hasNumeric = false;
            for (Map<String, Object> r : groupRows) {
                Object val = getRowValueOrEvaluate(r, colExpr);
                if (val != null) {
                    try {
                        sum += Double.parseDouble(val.toString());
                        hasNumeric = true;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return hasNumeric ? sum : null;
        }

        if ("AVG".equals(funcName)) {
            double sum = 0;
            long count = 0;
            for (Map<String, Object> r : groupRows) {
                Object val = getRowValueOrEvaluate(r, colExpr);
                if (val != null) {
                    try {
                        sum += Double.parseDouble(val.toString());
                        count++;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return count > 0 ? (sum / count) : null;
        }

        if ("MIN".equals(funcName)) {
            Double minNum = null;
            String minStr = null;
            boolean numeric = true;
            for (Map<String, Object> r : groupRows) {
                Object val = getRowValueOrEvaluate(r, colExpr);
                if (val != null) {
                    if (numeric) {
                        try {
                            double d = Double.parseDouble(val.toString());
                            if (minNum == null || d < minNum) minNum = d;
                        } catch (NumberFormatException e) {
                            numeric = false;
                            if (minStr == null || val.toString().compareTo(minStr) < 0) minStr = val.toString();
                        }
                    } else {
                        if (val.toString().compareTo(minStr) < 0) minStr = val.toString();
                    }
                }
            }
            if (numeric && minNum != null) return minNum;
            return minStr;
        }

        if ("MAX".equals(funcName)) {
            Double maxNum = null;
            String maxStr = null;
            boolean numeric = true;
            for (Map<String, Object> r : groupRows) {
                Object val = getRowValueOrEvaluate(r, colExpr);
                if (val != null) {
                    if (numeric) {
                        try {
                            double d = Double.parseDouble(val.toString());
                            if (maxNum == null || d > maxNum) maxNum = d;
                        } catch (NumberFormatException e) {
                            numeric = false;
                            if (maxStr == null || val.toString().compareTo(maxStr) > 0) maxStr = val.toString();
                        }
                    } else {
                        if (val.toString().compareTo(maxStr) > 0) maxStr = val.toString();
                    }
                }
            }
            if (numeric && maxNum != null) return maxNum;
            return maxStr;
        }

        if ("GROUP_CONCAT".equalsIgnoreCase(funcName)) {
            String expr = colExpr;
            String separator = ",";
            int sepIdx = expr.toUpperCase().indexOf(" SEPARATOR ");
            if (sepIdx != -1) {
                String sepPart = expr.substring(sepIdx + 11).trim();
                expr = expr.substring(0, sepIdx).trim();
                if ((sepPart.startsWith("'") && sepPart.endsWith("'")) || (sepPart.startsWith("\"") && sepPart.endsWith("\""))) {
                    sepPart = sepPart.substring(1, sepPart.length() - 1);
                }
                separator = sepPart;
            }

            String orderCol = null;
            boolean orderAsc = true;
            int orderIdx = expr.toUpperCase().indexOf(" ORDER BY ");
            if (orderIdx != -1) {
                String orderPart = expr.substring(orderIdx + 10).trim();
                expr = expr.substring(0, orderIdx).trim();
                if (orderPart.toUpperCase().endsWith(" DESC")) {
                    orderAsc = false;
                    orderPart = orderPart.substring(0, orderPart.length() - 5).trim();
                } else if (orderPart.toUpperCase().endsWith(" ASC")) {
                    orderAsc = true;
                    orderPart = orderPart.substring(0, orderPart.length() - 4).trim();
                }
                orderCol = orderPart;
            }

            List<Map<String, Object>> sortedRows = new ArrayList<>(groupRows);
            if (orderCol != null && !orderCol.isEmpty()) {
                final String oCol = orderCol;
                final boolean oAsc = orderAsc;
                sortedRows.sort((r1, r2) -> {
                    Object v1 = getRowValueOrEvaluate(r1, oCol);
                    Object v2 = getRowValueOrEvaluate(r2, oCol);
                    if (v1 == null && v2 == null) return 0;
                    if (v1 == null) return oAsc ? -1 : 1;
                    if (v2 == null) return oAsc ? 1 : -1;
                    int cmp = SqlCollation.compare(v1.toString(), v2.toString(), null);
                    return oAsc ? cmp : -cmp;
                });
            }

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            java.util.Set<String> seen = distinct ? new java.util.HashSet<>() : null;

            for (Map<String, Object> r : sortedRows) {
                Object val = getRowValueOrEvaluate(r, expr);
                if (val != null) {
                    String strVal = val.toString();
                    if (distinct) {
                        if (seen.contains(strVal)) continue;
                        seen.add(strVal);
                    }
                    if (!first) sb.append(separator);
                    sb.append(strVal);
                    first = false;
                }
            }
            return first ? null : sb.toString();
        }

        return null;
    }

    private void evaluateWindowFunctions(Command.Select select, List<Map<String, Object>> rows) {
        if (select == null || select.projection == null || rows == null || rows.isEmpty()) return;

        List<String> windowFuncExprs = new ArrayList<>();
        for (String rawProjItem : select.projection) {
            if (rawProjItem == null) continue;
            String projItem = rawProjItem;
            int asIdx = rawProjItem.toUpperCase().lastIndexOf(" AS ");
            if (asIdx != -1) {
                projItem = rawProjItem.substring(0, asIdx).trim();
            }

            String upper = projItem.toUpperCase();
            int idx = 0;
            while ((idx = upper.indexOf("OVER", idx)) != -1) {
                if (idx > 0 && !Character.isWhitespace(upper.charAt(idx - 1)) && upper.charAt(idx - 1) != ')') {
                    idx += 4;
                    continue;
                }
                
                int overStart = projItem.indexOf('(', idx);
                if (overStart == -1) { idx += 4; continue; }
                int parenCount = 1;
                int overEnd = -1;
                for (int i = overStart + 1; i < projItem.length(); i++) {
                    if (projItem.charAt(i) == '(') parenCount++;
                    else if (projItem.charAt(i) == ')') parenCount--;
                    if (parenCount == 0) {
                        overEnd = i;
                        break;
                    }
                }
                if (overEnd == -1) { idx += 4; continue; }
                
                int funcEnd = idx - 1;
                while (funcEnd >= 0 && Character.isWhitespace(projItem.charAt(funcEnd))) funcEnd--;
                if (funcEnd >= 0 && projItem.charAt(funcEnd) == ')') {
                    parenCount = 1;
                    int funcParenStart = -1;
                    for (int i = funcEnd - 1; i >= 0; i--) {
                        if (projItem.charAt(i) == ')') parenCount++;
                        else if (projItem.charAt(i) == '(') parenCount--;
                        if (parenCount == 0) {
                            funcParenStart = i;
                            break;
                        }
                    }
                    if (funcParenStart != -1) {
                        int funcNameStart = funcParenStart - 1;
                        while (funcNameStart >= 0 && (Character.isLetterOrDigit(projItem.charAt(funcNameStart)) || projItem.charAt(funcNameStart) == '_')) {
                            funcNameStart--;
                        }
                        funcNameStart++; // inclusive
                        
                        String fullExpr = projItem.substring(funcNameStart, overEnd + 1);
                        windowFuncExprs.add(fullExpr);
                    }
                }
                
                idx = overEnd + 1;
            }
        }

        for (String projItem : windowFuncExprs) {
            String upper = projItem.toUpperCase().trim();
            int overIdx = upper.lastIndexOf("OVER");
            if (overIdx == -1) continue; // Should not happen with our parser

            String funcPart = projItem.substring(0, overIdx).trim();
            String overPart = projItem.substring(overIdx + 4).trim();
            if (overPart.startsWith("(") && overPart.endsWith(")")) {
                overPart = overPart.substring(1, overPart.length() - 1).trim();
            }

            // --- Parse PARTITION BY ---
            List<String> partitionCols = new ArrayList<>();
            int partitionByIdx = overPart.toUpperCase().indexOf("PARTITION BY ");
            int orderByIdx = overPart.toUpperCase().indexOf("ORDER BY ");
            
            if (partitionByIdx != -1) {
                String partitionColsStr;
                if (orderByIdx != -1 && orderByIdx > partitionByIdx) {
                    partitionColsStr = overPart.substring(partitionByIdx + 13, orderByIdx).trim();
                } else {
                    partitionColsStr = overPart.substring(partitionByIdx + 13).trim();
                }
                for (String pCol : partitionColsStr.split(",")) {
                    if (!pCol.trim().isEmpty()) {
                        partitionCols.add(pCol.trim());
                    }
                }
            }

            // --- Parse ORDER BY col and optional frame spec (ROWS BETWEEN ... AND ...) ---
            String orderByCol = null;
            boolean asc = true;
            // Frame: default for window with ORDER BY is RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
            // (cumulative). ROWS BETWEEN N PRECEDING AND CURRENT ROW = sliding window of N+1 rows.
            int framePreceding = Integer.MAX_VALUE; // UNBOUNDED by default
            int frameFollowing = 0;                 // CURRENT ROW by default

            if (orderByIdx != -1) {
                String orderClause = overPart.substring(orderByIdx + 9).trim();

                // Extract ROWS BETWEEN ... AND ... frame if present
                java.util.regex.Matcher frameMatcher = java.util.regex.Pattern.compile(
                    "(?i)\\b(ROWS|RANGE)\\s+BETWEEN\\s+(.+?)\\s+AND\\s+(.+?)$"
                ).matcher(orderClause);
                if (frameMatcher.find()) {
                    // Strip the frame clause from orderClause
                    orderClause = orderClause.substring(0, frameMatcher.start()).trim();
                    String precedingStr = frameMatcher.group(2).trim().toUpperCase();
                    String followingStr = frameMatcher.group(3).trim().toUpperCase();
                    if (precedingStr.equals("UNBOUNDED PRECEDING")) {
                        framePreceding = Integer.MAX_VALUE;
                    } else if (precedingStr.matches("\\d+\\s+PRECEDING")) {
                        framePreceding = Integer.parseInt(precedingStr.replaceAll("\\s+PRECEDING", "").trim());
                    } else if (precedingStr.equals("CURRENT ROW")) {
                        framePreceding = 0;
                    }
                    if (followingStr.equals("UNBOUNDED FOLLOWING")) {
                        frameFollowing = Integer.MAX_VALUE;
                    } else if (followingStr.matches("\\d+\\s+FOLLOWING")) {
                        frameFollowing = Integer.parseInt(followingStr.replaceAll("\\s+FOLLOWING", "").trim());
                    } else if (followingStr.equals("CURRENT ROW")) {
                        frameFollowing = 0;
                    }
                }

                // Strip trailing ASC / DESC
                if (orderClause.toUpperCase().endsWith(" DESC")) {
                    asc = false;
                    orderByCol = orderClause.substring(0, orderClause.length() - 5).trim();
                } else if (orderClause.toUpperCase().endsWith(" ASC")) {
                    orderByCol = orderClause.substring(0, orderClause.length() - 4).trim();
                } else {
                    orderByCol = orderClause;
                }
                // Final trim in case frame stripping left trailing whitespace
                if (orderByCol != null) orderByCol = orderByCol.trim();
            }

            // Group rows by partition columns
            Map<String, List<Map<String, Object>>> partitions = new LinkedHashMap<>();
            if (partitionCols.isEmpty()) {
                partitions.put("all", new ArrayList<>(rows));
            } else {
                for (Map<String, Object> row : rows) {
                    StringBuilder partKey = new StringBuilder();
                    for (String pCol : partitionCols) {
                        Object val = getRowValueWithAlias(row, pCol, select.aliases);
                        if (val == null) val = row.get(pCol);
                        partKey.append(val).append("|");
                    }
                    partitions.computeIfAbsent(partKey.toString(), k -> new ArrayList<>()).add(row);
                }
            }
            
            rows.clear(); // We will re-add them after processing each partition
            
            for (List<Map<String, Object>> partRows : partitions.values()) {
                // Build and sort the window row list FOR THIS PARTITION
                List<Map<String, Object>> winRows = new ArrayList<>(partRows);
            if (orderByCol != null) {
                final String finalSortCol = orderByCol;
                final boolean finalAsc = asc;
                Collections.sort(winRows, new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> r1, Map<String, Object> r2) {
                        Object v1 = getRowValueWithAlias(r1, finalSortCol, select.aliases);
                        Object v2 = getRowValueWithAlias(r2, finalSortCol, select.aliases);
                        if (v1 == null) v1 = SqlFunctions.evaluate(finalSortCol, r1, DatabaseEngine.this);
                        if (v2 == null) v2 = SqlFunctions.evaluate(finalSortCol, r2, DatabaseEngine.this);

                        if (v1 == null && v2 == null) return 0;
                        if (v1 == null) return finalAsc ? -1 : 1;
                        if (v2 == null) return finalAsc ? 1 : -1;

                        int cmp;
                        if (v1 instanceof Number && v2 instanceof Number) {
                            cmp = Double.compare(((Number) v1).doubleValue(), ((Number) v2).doubleValue());
                        } else {
                            cmp = SqlCollation.compare(v1.toString(), v2.toString(), "utf8mb4_general_ci");
                        }
                        return finalAsc ? cmp : -cmp;
                    }
                });
            }

            String funcExpr = projItem;
            int funcAsIdx = funcExpr.toUpperCase().indexOf(" AS ");
            if (funcAsIdx != -1) {
                funcExpr = funcExpr.substring(0, funcAsIdx).trim();
            }
            String normalizedFuncExpr = null;
            try {
                normalizedFuncExpr = SqlFunctions.parse(funcExpr).toString();
            } catch (Exception e) {
                normalizedFuncExpr = funcExpr;
            }

            String funcUpper = funcPart.toUpperCase().trim();

            if (funcUpper.startsWith("SUM(")) {
                String col = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                for (int i = 0; i < winRows.size(); i++) {
                    int frameStart = (framePreceding == Integer.MAX_VALUE) ? 0 : Math.max(0, i - framePreceding);
                    int frameEnd = (frameFollowing == Integer.MAX_VALUE) ? winRows.size() - 1 : Math.min(winRows.size() - 1, i + frameFollowing);
                    double runningSum = 0;
                    boolean hasVal = false;
                    for (int j = frameStart; j <= frameEnd; j++) {
                        Object val = getRowValueOrEvaluate(winRows.get(j), col);
                        if (val != null) {
                            try { runningSum += Double.parseDouble(val.toString()); hasVal = true; }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                    putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, hasVal ? runningSum : null);
                }
            } else if (funcUpper.startsWith("AVG(")) {
                String col = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                for (int i = 0; i < winRows.size(); i++) {
                    int frameStart = (framePreceding == Integer.MAX_VALUE) ? 0 : Math.max(0, i - framePreceding);
                    int frameEnd = (frameFollowing == Integer.MAX_VALUE) ? winRows.size() - 1 : Math.min(winRows.size() - 1, i + frameFollowing);
                    double sum = 0;
                    long count = 0;
                    for (int j = frameStart; j <= frameEnd; j++) {
                        Object val = getRowValueOrEvaluate(winRows.get(j), col);
                        if (val != null) {
                            try { sum += Double.parseDouble(val.toString()); count++; }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                    putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, count > 0 ? (sum / count) : null);
                }
            } else if (funcUpper.startsWith("COUNT(")) {
                long count = 0;
                for (Map<String, Object> row : winRows) {
                    count++;
                    putWindowResult(row, select, projItem, normalizedFuncExpr, count);
                }
            } else if (funcUpper.startsWith("ROW_NUMBER()")) {
                long rn = 1;
                for (Map<String, Object> row : winRows) {
                    putWindowResult(row, select, projItem, normalizedFuncExpr, rn++);
                }
            } else if (funcUpper.startsWith("DENSE_RANK()")) {
                long rank = 0;
                Object prevVal = null;
                boolean first = true;
                for (Map<String, Object> row : winRows) {
                    Object curVal = orderByCol != null ? getRowValueWithAlias(row, orderByCol, select.aliases) : null;
                    if (curVal == null && orderByCol != null) curVal = row.get(orderByCol);
                    if (first || (curVal == null ? prevVal != null : !curVal.equals(prevVal))) {
                        rank++;
                        prevVal = curVal;
                        first = false;
                    }
                    putWindowResult(row, select, projItem, normalizedFuncExpr, rank);
                }
            } else if (funcUpper.startsWith("RANK()")) {
                long rank = 1;
                long pos = 1;
                Object prevVal = null;
                boolean first = true;
                for (Map<String, Object> row : winRows) {
                    Object curVal = orderByCol != null ? getRowValueWithAlias(row, orderByCol, select.aliases) : null;
                    if (curVal == null && orderByCol != null) curVal = row.get(orderByCol);
                    if (first) {
                        rank = 1;
                        prevVal = curVal;
                        first = false;
                    } else if (curVal == null ? prevVal != null : !curVal.equals(prevVal)) {
                        rank = pos;
                        prevVal = curVal;
                    }
                    pos++;
                    putWindowResult(row, select, projItem, normalizedFuncExpr, rank);
                }
            } else if (funcUpper.startsWith("PERCENT_RANK()")) {
                long rank = 1;
                long pos = 1;
                Object prevVal = null;
                boolean first = true;
                for (Map<String, Object> row : winRows) {
                    Object curVal = orderByCol != null ? getRowValueWithAlias(row, orderByCol, select.aliases) : null;
                    if (curVal == null && orderByCol != null) curVal = row.get(orderByCol);
                    if (first) {
                        rank = 1;
                        prevVal = curVal;
                        first = false;
                    } else if (curVal == null ? prevVal != null : !curVal.equals(prevVal)) {
                        rank = pos;
                        prevVal = curVal;
                    }
                    pos++;
                    double pr = winRows.size() > 1 ? (double)(rank - 1) / (winRows.size() - 1) : 0.0;
                    putWindowResult(row, select, projItem, normalizedFuncExpr, pr);
                }
            } else if (funcUpper.startsWith("NTILE(")) {
                String argStr = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                int numBuckets = 1;
                try { numBuckets = Integer.parseInt(argStr); } catch (NumberFormatException ignored) {}
                if (numBuckets < 1) numBuckets = 1;
                int totalRows = winRows.size();
                int baseSize = totalRows / numBuckets;
                int rem = totalRows % numBuckets;
                int currentBucket = 1;
                int currentBucketRows = 0;
                for (Map<String, Object> row : winRows) {
                    int limit = baseSize + (currentBucket <= rem ? 1 : 0);
                    putWindowResult(row, select, projItem, normalizedFuncExpr, (long)currentBucket);
                    currentBucketRows++;
                    if (currentBucketRows >= limit && currentBucket < numBuckets) {
                        currentBucket++;
                        currentBucketRows = 0;
                    }
                }
            } else if (funcUpper.startsWith("LAG(")) {
                String inner = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                List<String> lagArgs = parseWindowArgs(inner);
                if (!lagArgs.isEmpty()) {
                    String expr = lagArgs.get(0);
                    int offset = lagArgs.size() >= 2 ? Integer.parseInt(lagArgs.get(1)) : 1;
                    Object defaultVal = lagArgs.size() >= 3 ? lagArgs.get(2) : null;
                    for (int i = 0; i < winRows.size(); i++) {
                        int targetIdx = i - offset;
                        Object resVal = defaultVal;
                        if (targetIdx >= 0 && targetIdx < winRows.size()) {
                            Object v = getRowValueOrEvaluate(winRows.get(targetIdx), expr);
                            if (v != null) resVal = v;
                        }
                        putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, resVal);
                    }
                }
            } else if (funcUpper.startsWith("LEAD(")) {
                String inner = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                List<String> leadArgs = parseWindowArgs(inner);
                if (!leadArgs.isEmpty()) {
                    String expr = leadArgs.get(0);
                    int offset = leadArgs.size() >= 2 ? Integer.parseInt(leadArgs.get(1)) : 1;
                    Object defaultVal = leadArgs.size() >= 3 ? leadArgs.get(2) : null;
                    for (int i = 0; i < winRows.size(); i++) {
                        int targetIdx = i + offset;
                        Object resVal = defaultVal;
                        if (targetIdx >= 0 && targetIdx < winRows.size()) {
                            Object v = getRowValueOrEvaluate(winRows.get(targetIdx), expr);
                            if (v != null) resVal = v;
                        }
                        putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, resVal);
                    }
                }
            } else if (funcUpper.startsWith("FIRST_VALUE(")) {
                String expr = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                for (int i = 0; i < winRows.size(); i++) {
                    int frameStart = (framePreceding == Integer.MAX_VALUE) ? 0 : Math.max(0, i - framePreceding);
                    Object resVal = getRowValueOrEvaluate(winRows.get(frameStart), expr);
                    putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, resVal);
                }
            } else if (funcUpper.startsWith("LAST_VALUE(")) {
                String expr = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                for (int i = 0; i < winRows.size(); i++) {
                    int frameEnd = (frameFollowing == Integer.MAX_VALUE) ? winRows.size() - 1 : Math.min(winRows.size() - 1, i + frameFollowing);
                    Object resVal = getRowValueOrEvaluate(winRows.get(frameEnd), expr);
                    putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, resVal);
                }
            } else if (funcUpper.startsWith("NTH_VALUE(")) {
                String inner = funcPart.substring(funcPart.indexOf('(') + 1, funcPart.lastIndexOf(')')).trim();
                List<String> nthArgs = parseWindowArgs(inner);
                if (nthArgs.size() >= 2) {
                    String expr = nthArgs.get(0);
                    int n = Integer.parseInt(nthArgs.get(1));
                    for (int i = 0; i < winRows.size(); i++) {
                        int targetIdx = n - 1;
                        Object resVal = null;
                        if (targetIdx >= 0 && targetIdx < winRows.size()) {
                            resVal = getRowValueOrEvaluate(winRows.get(targetIdx), expr);
                        }
                        putWindowResult(winRows.get(i), select, projItem, normalizedFuncExpr, resVal);
                    }
                }
            }
            rows.addAll(winRows);
            }
        }
    }

    private void putWindowResult(Map<String, Object> row, Command.Select select, String projItem, String funcExpr, Object resVal) {
        if (projItem != null) {
            row.put(projItem, resVal);
            row.put(projItem.replaceAll("\\s+", ""), resVal);
        }
        if (funcExpr != null) {
            row.put(funcExpr, resVal);
            row.put(funcExpr.replaceAll("\\s+", ""), resVal);
        }
        if (select != null && select.aliases != null) {
            for (Map.Entry<String, String> e : select.aliases.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k != null) {
                    String cleanK = k.replaceAll("\\s+", "");
                    String cleanP = projItem != null ? projItem.replaceAll("\\s+", "") : "";
                    String cleanF = funcExpr != null ? funcExpr.replaceAll("\\s+", "") : "";
                    if (k.equals(projItem) || k.equals(funcExpr) || cleanK.equalsIgnoreCase(cleanP) || cleanK.equalsIgnoreCase(cleanF)) {
                        if (v != null) row.put(v, resVal);
                        row.put(k, resVal);
                    }
                }
            }
        }
    }

    private List<String> parseWindowArgs(String inner) {
        List<String> args = new ArrayList<>();
        if (inner == null || inner.trim().isEmpty()) return args;
        StringBuilder sb = new StringBuilder();
        int parenDepth = 0;
        boolean inStr = false;
        char strChar = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inStr) {
                sb.append(c);
                if (c == strChar) inStr = false;
            } else if (c == '\'' || c == '"') {
                inStr = true; strChar = c; sb.append(c);
            } else if (c == '(') {
                parenDepth++; sb.append(c);
            } else if (c == ')') {
                if (parenDepth > 0) parenDepth--; sb.append(c);
            } else if (c == ',' && parenDepth == 0) {
                args.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            args.add(sb.toString().trim());
        }
        return args;
    }

    private String resolveColumnType(Command.Select select, String colName) {
        if (select.tableName == null) return "TEXT";
        try {
            if (colName.contains(".")) {
                String tbl = colName.substring(0, colName.indexOf('.'));
                String col = colName.substring(colName.indexOf('.') + 1);
                TableData td = getOrLoadTable(tbl);
                int jidx = td.columns.indexOf(col);
                if (jidx != -1) {
                    return td.types.get(jidx);
                }
            } else {
                TableData baseTd = getOrLoadTable(select.tableName);
                int idx = baseTd.columns.indexOf(colName);
                if (idx != -1) {
                    return baseTd.types.get(idx);
                }
                if (select.joins != null) {
                    for (Clause.Join join : select.joins) {
                        TableData jtd = getOrLoadTable(join.table);
                        int jidx = jtd.columns.indexOf(colName);
                        if (jidx != -1) {
                            return jtd.types.get(jidx);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "TEXT";
    }

    private void collectWhereColumns(Clause.Where where, List<String> columns) {
        if (where == null) return;
        if (where.logicalOperator != null) {
            if (where.subConditions != null) {
                for (Clause.Where sub : where.subConditions) {
                    collectWhereColumns(sub, columns);
                }
            }
        } else {
            if (where.column != null) {
                try {
                    SqlFunctions.Expression expr = SqlFunctions.parse(where.column);
                    expr.collectColumns(columns);
                } catch (Exception e) {
                    columns.add(where.column);
                }
            }
            if (where.isValueColumn && where.value instanceof String) {
                String valCol = (String) where.value;
                if (!valCol.toUpperCase().contains("SELECT ")) {
                    try {
                        SqlFunctions.Expression expr = SqlFunctions.parse(valCol);
                        expr.collectColumns(columns);
                    } catch (Exception e) {
                        columns.add(valCol);
                    }
                }
            }
        }
    }

    private boolean isValidColumnReference(String col, Map<String, List<String>> tableColumnsMap, List<String> allUnqualifiedColumns, Set<String> localVariables) {
        if (col == null || col.equals("*") || col.contains("*") || col.startsWith("@")) {
            return true;
        }
        String colLower = col.toLowerCase();
        if (localVariables != null) {
            for (String var : localVariables) {
                if (var.equalsIgnoreCase(colLower)) {
                    return true;
                }
            }
        }
        // Allow system/constant identifiers and datepart keywords
        if ("system_user".equals(colLower) || "session_user".equals(colLower) || 
            "current_user".equals(colLower) || "database".equals(colLower) || 
            "version".equals(colLower) || "connection_id".equals(colLower) ||
            "weekday".equals(colLower) || "dw".equals(colLower) || "w".equals(colLower) ||
            "year".equals(colLower) || "yy".equals(colLower) || "yyyy".equals(colLower) ||
            "month".equals(colLower) || "mm".equals(colLower) || "m".equals(colLower) ||
            "day".equals(colLower) || "dd".equals(colLower) || "d".equals(colLower) ||
            "hour".equals(colLower) || "hh".equals(colLower) ||
            "minute".equals(colLower) || "mi".equals(colLower) || "n".equals(colLower) ||
            "second".equals(colLower) || "ss".equals(colLower) || "s".equals(colLower) ||
            "quarter".equals(colLower) || "qq".equals(colLower) || "q".equals(colLower) ||
            "dayofyear".equals(colLower) || "dy".equals(colLower)) {
            return true;
        }

        if (col.contains(".")) {
            String prefix = col.substring(0, col.indexOf('.')).toLowerCase();
            String suffix = col.substring(col.indexOf('.') + 1).toLowerCase();
            if (tableColumnsMap.containsKey(prefix)) {
                return tableColumnsMap.get(prefix).contains(suffix);
            }
            return false;
        } else {
            return allUnqualifiedColumns.contains(colLower);
        }
    }

    private List<String> expandProjectionList(Command.Select select) {
        if (select == null || select.projection == null) return null;
        List<String> expanded = new ArrayList<>();
        for (String item : select.projection) {
            String trimmed = item.trim();
            if ("*".equals(trimmed)) {
                if (select.tableName != null) {
                    try {
                        String realTable = resolveTableName(select.tableName);
                        TableData baseTd = getOrLoadTable(realTable);
                        if (baseTd != null) {
                            for (String col : baseTd.columns) {
                                if (isColumnVisible(realTable, col)) {
                                    expanded.add(col);
                                }
                            }
                        }
                    } catch (Exception e) {}
                    if (select.joins != null) {
                        for (Clause.Join join : select.joins) {
                            try {
                                String realJoin = resolveTableName(join.table);
                                TableData jtd = getOrLoadTable(realJoin);
                                if (jtd != null) {
                                    for (String col : jtd.columns) {
                                        if (isColumnVisible(realJoin, col)) {
                                            expanded.add(col);
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }
                } else {
                    expanded.add(item);
                }
            } else if (trimmed.endsWith(".*")) {
                String prefix = trimmed.substring(0, trimmed.length() - 2).trim();
                String targetTable = prefix;
                if (select.tableAliases != null && select.tableAliases.containsKey(prefix)) {
                    targetTable = select.tableAliases.get(prefix);
                }
                TableData td = null;
                try {
                    String realTarget = resolveTableName(targetTable);
                    td = getOrLoadTable(realTarget);
                } catch (Exception e) {}
                if (td != null) {
                    for (String col : td.columns) {
                        if (isColumnVisible(targetTable, col)) {
                            expanded.add(col);
                        }
                    }
                } else {
                    expanded.add(item);
                }
            } else {
                expanded.add(item);
            }
        }
        return expanded;
    }

    private void validateColumnReferences(Command.Select select) throws Exception {
        validateColumnReferences(select, java.util.Collections.emptySet());
    }

    private void validateColumnReferences(Command.Select select, Set<String> localVariables) throws Exception {
        // 1. Load all tables involved in the SELECT
        List<TableData> tables = new ArrayList<>();
        if (select.tableName != null && !select.tableName.isEmpty()) {
            tables.add(getOrLoadTable(select.tableName));
        }
        if (select.joins != null) {
            for (Clause.Join join : select.joins) {
                tables.add(getOrLoadTable(join.table));
            }
        }

        // 2. Build maps of valid columns
        Map<String, List<String>> tableColumnsMap = new HashMap<>();
        List<String> allUnqualifiedColumns = new ArrayList<>();

        for (TableData td : tables) {
            String tblNameLower = td.tableName.toLowerCase();
            List<String> colsLower = new ArrayList<>();
            for (String c : td.columns) {
                colsLower.add(c.toLowerCase());
            }
            tableColumnsMap.put(tblNameLower, colsLower);

            // Add alias mappings
            if (select.tableAliases != null) {
                for (Map.Entry<String, String> entry : select.tableAliases.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(td.tableName)) {
                        tableColumnsMap.put(entry.getKey().toLowerCase(), colsLower);
                    }
                }
            }
            // For joins, we also have join.alias
            if (select.joins != null) {
                for (Clause.Join join : select.joins) {
                    if (join.table.equalsIgnoreCase(td.tableName) && join.alias != null) {
                        tableColumnsMap.put(join.alias.toLowerCase(), colsLower);
                    }
                }
            }

            allUnqualifiedColumns.addAll(colsLower);
        }

        // 3. Collect columns from clauses
        List<String> referencedCols = new ArrayList<>();
        List<String> orderByCols = new ArrayList<>();

        // Collect from projection
        if (select.projection != null) {
            for (String item : select.projection) {
                if (item.equals("*") || item.contains("*")) {
                    continue;
                }
                SqlFunctions.Expression expr = SqlFunctions.parse(item);
                expr.collectColumns(referencedCols);
            }
        }

        // Collect from WHERE
        if (select.where != null) {
            collectWhereColumns(select.where, referencedCols);
        }

        // Collect from GROUP BY
        if (select.groupBy != null) {
            List<String> gCols = select.groupBy.columns != null ? select.groupBy.columns : java.util.Collections.singletonList(select.groupBy.column);
            for (String gCol : gCols) {
                if (gCol != null) {
                    String realExpr = gCol;
                    if (select.aliases != null) {
                        for (Map.Entry<String, String> entry : select.aliases.entrySet()) {
                            if (entry.getValue().equalsIgnoreCase(gCol)) {
                                realExpr = entry.getKey();
                                break;
                            }
                        }
                    }
                    if (realExpr.contains("(")) {
                        try {
                            SqlFunctions.Expression expr = SqlFunctions.parse(realExpr);
                            expr.collectColumns(referencedCols);
                        } catch (Exception ignored) {
                            referencedCols.add(realExpr);
                        }
                    } else if (!gCol.equalsIgnoreCase(realExpr)) {
                        referencedCols.add(realExpr);
                    } else {
                        referencedCols.add(gCol);
                    }
                }
            }
        }

        // Collect from HAVING
        if (select.having != null) {
            SqlFunctions.Expression expr = SqlFunctions.parse(select.having.aggregateFunc);
            expr.collectColumns(referencedCols);
        }

        // Collect from Joins
        if (select.joins != null) {
            for (Clause.Join join : select.joins) {
                if (join.leftCol != null) {
                    referencedCols.add(join.leftCol);
                }
                if (join.rightCol != null) {
                    referencedCols.add(join.rightCol);
                }
                if (join.extraConditions != null) {
                    for (Clause.Where cond : join.extraConditions) {
                        collectWhereColumns(cond, referencedCols);
                    }
                }
            }
        }

        // Collect from ORDER BY
        if (select.orderBySpecs != null) {
            for (Clause.OrderBy spec : select.orderBySpecs) {
                orderByCols.add(spec.column);
            }
        }

        // 4. Validate referenced columns (which cannot be projection aliases)
        List<String> projectionAliases = new ArrayList<>();
        if (select.aliases != null) {
            for (String alias : select.aliases.values()) {
                projectionAliases.add(alias.toLowerCase());
            }
        }

        for (String col : referencedCols) {
            if (col == null || col.trim().isEmpty()) continue;
            if (projectionAliases.contains(col.toLowerCase())) {
                continue;
            }
            if (!isValidColumnReference(col, tableColumnsMap, allUnqualifiedColumns, localVariables)) {
                throw new Exception("Unknown column '" + col + "' in 'field list'");
            }
        }

        // 5. Validate ORDER BY columns (which CAN be projection aliases or function expressions)
        for (String col : orderByCols) {
            if (col == null || col.trim().isEmpty()) continue;
            if (projectionAliases.contains(col.toLowerCase())) {
                continue;
            }
            if (col.contains("(")) {
                try {
                    SqlFunctions.Expression expr = SqlFunctions.parse(col);
                    List<String> exprCols = new ArrayList<>();
                    expr.collectColumns(exprCols);
                    for (String ec : exprCols) {
                        if (!isValidColumnReference(ec, tableColumnsMap, allUnqualifiedColumns, localVariables)) {
                            throw new Exception("Unknown column '" + ec + "' in 'order clause'");
                        }
                    }
                    continue;
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Unknown column")) throw e;
                }
            }
            if (!isValidColumnReference(col, tableColumnsMap, allUnqualifiedColumns, localVariables)) {
                throw new Exception("Unknown column '" + col + "' in 'order clause'");
            }
        }

        // 6. Recursively validate UNION subqueries
        if (select.union != null && select.union.selectQuery != null) {
            validateColumnReferences(select.union.selectQuery, localVariables);
        }
    }

    private void validateProcedureOrFunction(List<SqlToken> tokens, Set<String> parameterNames) throws Exception {
        Set<String> localVars = new HashSet<>();
        if (parameterNames != null) {
            for (String p : parameterNames) {
                localVars.add(p.toLowerCase());
            }
        }

        // Collect declared variables
        for (int i = 0; i < tokens.size(); i++) {
            SqlToken tok = tokens.get(i);
            if (tok.type == SqlToken.Type.KEYWORD && "DECLARE".equalsIgnoreCase(tok.value)) {
                if (i + 1 < tokens.size()) {
                    String nextVal = tokens.get(i + 1).value.toUpperCase();
                    if (nextVal.equals("EXIT") || nextVal.equals("CONTINUE") || nextVal.equals("HANDLER")) {
                        continue;
                    }
                    int j = i + 1;
                    while (j < tokens.size()) {
                        SqlToken varTok = tokens.get(j);
                        if (varTok.type == SqlToken.Type.IDENTIFIER || varTok.type == SqlToken.Type.KEYWORD) {
                            localVars.add(varTok.value.toLowerCase());
                        }
                        if (j + 1 < tokens.size() && tokens.get(j + 1).value.equals(",")) {
                            j += 2;
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        // Find and validate all SELECT statements
        for (int i = 0; i < tokens.size(); i++) {
            SqlToken tok = tokens.get(i);
            if (tok.type == SqlToken.Type.KEYWORD && "SELECT".equalsIgnoreCase(tok.value)) {
                List<SqlToken> cleanTokens = extractAndCleanSelectTokens(tokens, i);
                SqlParser parser = new SqlParser(cleanTokens);
                Command cmd = parser.parse();
                if (cmd instanceof Command.Select) {
                    validateColumnReferences((Command.Select) cmd, localVars);
                }
            }
        }
    }

    private List<SqlToken> extractAndCleanSelectTokens(List<SqlToken> tokens, int startIndex) {
        List<SqlToken> cleanTokens = new ArrayList<>();
        int parenDepth = 0;
        int pos = startIndex;
        boolean skippingInto = false;

        while (pos < tokens.size()) {
            SqlToken tok = tokens.get(pos);

            // Check for statement end or exiting a subquery
            if (parenDepth < 0 || (parenDepth == 0 && (";".equals(tok.value) || "END".equalsIgnoreCase(tok.value)))) {
                break;
            }

            if (tok.value.equals("(")) {
                parenDepth++;
            } else if (tok.value.equals(")")) {
                parenDepth--;
                if (parenDepth < 0) {
                    break;
                }
            }

            if (tok.type == SqlToken.Type.KEYWORD && "INTO".equalsIgnoreCase(tok.value)) {
                skippingInto = true;
                pos++;
                continue;
            }

            if (skippingInto) {
                if (tok.type == SqlToken.Type.KEYWORD && "FROM".equalsIgnoreCase(tok.value)) {
                    skippingInto = false;
                } else {
                    pos++;
                    continue;
                }
            }

            cleanTokens.add(tok);
            pos++;
        }

        int lastPos = tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).position;
        cleanTokens.add(new SqlToken(SqlToken.Type.EOF, "", lastPos));
        return cleanTokens;
    }


    public QueryResult selectFrom(Command.Select select) throws Exception {
        List<String> tempTablesCreated = new ArrayList<>();
        try {
            if (select.derivedTableQuery != null) {
                QueryResult subRes = selectFrom(select.derivedTableQuery);
                if (!subRes.success) {
                    return subRes;
                }
                String dtName = select.derivedTableAlias != null ? select.derivedTableAlias : select.tableName;
                if (dtName == null || dtName.isEmpty()) {
                    dtName = "_derived_" + System.currentTimeMillis();
                }
                createTableFromQueryResult(dtName, subRes);
                tempTablesCreated.add(dtName);
            }

            if (select.joins != null) {
                for (Clause.Join join : select.joins) {
                    if (join.derivedTableQuery != null) {
                        QueryResult joinRes = selectFrom(join.derivedTableQuery);
                        if (!joinRes.success) {
                            return joinRes;
                        }
                        createTableFromQueryResult(join.table, joinRes);
                        tempTablesCreated.add(join.table);
                    }
                }
            }

            validateColumnReferences(select);

            if (select.tableName != null) {
                verifyPrivilege("SELECT", activeDatabaseName, select.tableName);
            }

            List<Map<String, Object>> joinedRows = new ArrayList<>();
            if (select.tableName != null && !select.tableName.isEmpty()) {
                TableData baseTd = getOrLoadTable(select.tableName);
                String baseAlias = null;
                if (select.tableAliases != null) {
                    for (Map.Entry<String, String> entry : select.tableAliases.entrySet()) {
                        if (entry.getValue().equalsIgnoreCase(select.tableName)) {
                            baseAlias = entry.getKey();
                            break;
                        }
                    }
                }
                for (Map<String, Object> row : baseTd.rows) {
                    Map<String, Object> newRow = new HashMap<>();
                    for (String col : baseTd.columns) {
                        newRow.put(col, row.get(col));
                        newRow.put(select.tableName + "." + col, row.get(col));
                        if (baseAlias != null) {
                            newRow.put(baseAlias + "." + col, row.get(col));
                        }
                    }
                    joinedRows.add(newRow);
                }
            } else {
                Map<String, Object> emptyRow = new HashMap<>();
                joinedRows.add(emptyRow);
            }

        // 1. Process Joins
        if (select.joins != null) {
            for (Clause.Join join : select.joins) {
                verifyPrivilege("SELECT", activeDatabaseName, join.table);
                TableData rightTd = getOrLoadTable(join.table);
                List<Map<String, Object>> nextJoinedRows = new ArrayList<>();

                if ("CROSS".equals(join.type)) {
                    for (Map<String, Object> leftRow : joinedRows) {
                        for (Map<String, Object> rightRow : rightTd.rows) {
                            Map<String, Object> combined = new HashMap<>(leftRow);
                            for (String col : rightTd.columns) {
                                combined.put(join.table + "." + col, rightRow.get(col));
                                if (join.alias != null) {
                                    combined.put(join.alias + "." + col, rightRow.get(col));
                                }
                                if (!combined.containsKey(col)) {
                                    combined.put(col, rightRow.get(col));
                                }
                            }
                            nextJoinedRows.add(combined);
                        }
                    }
                } else {
                    // Try to identify standard or swapped key positions for Hash Join
                    Boolean standardOrder = null;
                    if (!joinedRows.isEmpty() && !rightTd.rows.isEmpty()) {
                        Map<String, Object> sampleLeft = joinedRows.get(0);
                        Map<String, Object> sampleRight = rightTd.rows.get(0);
                        if (rowHasColumn(sampleLeft, join.leftCol) && rowHasColumn(sampleRight, join.rightCol)) {
                            standardOrder = true;
                        } else if (rowHasColumn(sampleLeft, join.rightCol) && rowHasColumn(sampleRight, join.leftCol)) {
                            standardOrder = false;
                        }
                    }

                    if (standardOrder != null) {
                        String leftColExpr = standardOrder ? join.leftCol : join.rightCol;
                        String rightColExpr = standardOrder ? join.rightCol : join.leftCol;
                        String collation = resolveCollation(null, leftColExpr);

                        if ("INNER".equals(join.type)) {
                            // Build right table hash map
                            Map<Object, List<Map<String, Object>>> rightMap = new HashMap<>();
                            for (Map<String, Object> rightRow : rightTd.rows) {
                                Object rawVal = getRowValue(rightRow, rightColExpr);
                                Object key = TableData.getCollationKey(rawVal, collation);
                                if (key != null) {
                                    List<Map<String, Object>> list = rightMap.get(key);
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        rightMap.put(key, list);
                                    }
                                    list.add(rightRow);
                                }
                            }

                            // Probe right map
                            for (Map<String, Object> leftRow : joinedRows) {
                                Object rawVal = getRowValue(leftRow, leftColExpr);
                                Object key = TableData.getCollationKey(rawVal, collation);
                                if (key != null) {
                                    List<Map<String, Object>> matchedRightRows = rightMap.get(key);
                                    if (matchedRightRows != null) {
                                        for (Map<String, Object> rightRow : matchedRightRows) {
                                            if (joinConditionMatches(leftRow, rightRow, join)) {
                                                Map<String, Object> combined = new HashMap<>(leftRow);
                                                for (String col : rightTd.columns) {
                                                    combined.put(join.table + "." + col, rightRow.get(col));
                                                    if (join.alias != null) {
                                                        combined.put(join.alias + "." + col, rightRow.get(col));
                                                    }
                                                    if (!combined.containsKey(col)) {
                                                        combined.put(col, rightRow.get(col));
                                                    }
                                                }
                                                nextJoinedRows.add(combined);
                                            }
                                        }
                                    }
                                }
                            }
                        } else if ("LEFT".equals(join.type)) {
                            // Build right table hash map
                            Map<Object, List<Map<String, Object>>> rightMap = new HashMap<>();
                            for (Map<String, Object> rightRow : rightTd.rows) {
                                Object rawVal = getRowValue(rightRow, rightColExpr);
                                Object key = TableData.getCollationKey(rawVal, collation);
                                if (key != null) {
                                    List<Map<String, Object>> list = rightMap.get(key);
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        rightMap.put(key, list);
                                    }
                                    list.add(rightRow);
                                }
                            }

                            // Probe right map
                            for (Map<String, Object> leftRow : joinedRows) {
                                boolean matched = false;
                                Object rawVal = getRowValue(leftRow, leftColExpr);
                                Object key = TableData.getCollationKey(rawVal, collation);
                                if (key != null) {
                                    List<Map<String, Object>> matchedRightRows = rightMap.get(key);
                                    if (matchedRightRows != null) {
                                        for (Map<String, Object> rightRow : matchedRightRows) {
                                            if (joinConditionMatches(leftRow, rightRow, join)) {
                                                Map<String, Object> combined = new HashMap<>(leftRow);
                                                for (String col : rightTd.columns) {
                                                    combined.put(join.table + "." + col, rightRow.get(col));
                                                    if (join.alias != null) {
                                                        combined.put(join.alias + "." + col, rightRow.get(col));
                                                    }
                                                    if (!combined.containsKey(col)) {
                                                        combined.put(col, rightRow.get(col));
                                                    }
                                                }
                                                nextJoinedRows.add(combined);
                                                matched = true;
                                            }
                                        }
                                    }
                                }
                                if (!matched) {
                                    Map<String, Object> combined = new HashMap<>(leftRow);
                                    for (String col : rightTd.columns) {
                                        combined.put(join.table + "." + col, null);
                                        if (join.alias != null) {
                                            combined.put(join.alias + "." + col, null);
                                        }
                                        if (!combined.containsKey(col)) {
                                            combined.put(col, null);
                                        }
                                    }
                                    nextJoinedRows.add(combined);
                                }
                            }
                        } else if ("RIGHT".equals(join.type)) {
                            // Build left table hash map
                            Map<Object, List<Map<String, Object>>> leftMap = new HashMap<>();
                            for (Map<String, Object> leftRow : joinedRows) {
                                Object rawVal = getRowValue(leftRow, leftColExpr);
                                Object key = TableData.getCollationKey(rawVal, collation);
                                if (key != null) {
                                    List<Map<String, Object>> list = leftMap.get(key);
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        leftMap.put(key, list);
                                    }
                                    list.add(leftRow);
                                }
                            }

                            // Probe left map
                            for (Map<String, Object> rightRow : rightTd.rows) {
                                boolean matched = false;
                                Object rawVal = getRowValue(rightRow, rightColExpr);
                                Object key = TableData.getCollationKey(rawVal, collation);
                                if (key != null) {
                                    List<Map<String, Object>> matchedLeftRows = leftMap.get(key);
                                    if (matchedLeftRows != null) {
                                        for (Map<String, Object> leftRow : matchedLeftRows) {
                                            if (joinConditionMatches(leftRow, rightRow, join)) {
                                                Map<String, Object> combined = new HashMap<>(leftRow);
                                                for (String col : rightTd.columns) {
                                                    combined.put(join.table + "." + col, rightRow.get(col));
                                                    if (join.alias != null) {
                                                        combined.put(join.alias + "." + col, rightRow.get(col));
                                                    }
                                                    if (!combined.containsKey(col)) {
                                                        combined.put(col, rightRow.get(col));
                                                    }
                                                }
                                                nextJoinedRows.add(combined);
                                                matched = true;
                                            }
                                        }
                                    }
                                }
                                if (!matched) {
                                    Map<String, Object> combined = new HashMap<>();
                                    if (!joinedRows.isEmpty()) {
                                        for (String k : joinedRows.get(0).keySet()) {
                                            combined.put(k, null);
                                        }
                                    }
                                    for (String col : rightTd.columns) {
                                        combined.put(join.table + "." + col, rightRow.get(col));
                                        if (join.alias != null) {
                                            combined.put(join.alias + "." + col, rightRow.get(col));
                                        }
                                        combined.put(col, rightRow.get(col));
                                    }
                                    nextJoinedRows.add(combined);
                                }
                            }
                        }
                    } else {
                        // Fallback to nested loops if standard/swapped order cannot be determined
                        if ("INNER".equals(join.type)) {
                            for (Map<String, Object> leftRow : joinedRows) {
                                for (Map<String, Object> rightRow : rightTd.rows) {
                                    if (joinConditionMatches(leftRow, rightRow, join)) {
                                        Map<String, Object> combined = new HashMap<>(leftRow);
                                        for (String col : rightTd.columns) {
                                            combined.put(join.table + "." + col, rightRow.get(col));
                                            if (join.alias != null) {
                                                combined.put(join.alias + "." + col, rightRow.get(col));
                                            }
                                            if (!combined.containsKey(col)) {
                                                combined.put(col, rightRow.get(col));
                                            }
                                        }
                                        nextJoinedRows.add(combined);
                                    }
                                }
                            }
                        } else if ("LEFT".equals(join.type)) {
                            for (Map<String, Object> leftRow : joinedRows) {
                                boolean matched = false;
                                for (Map<String, Object> rightRow : rightTd.rows) {
                                    if (joinConditionMatches(leftRow, rightRow, join)) {
                                        Map<String, Object> combined = new HashMap<>(leftRow);
                                        for (String col : rightTd.columns) {
                                            combined.put(join.table + "." + col, rightRow.get(col));
                                            if (join.alias != null) {
                                                combined.put(join.alias + "." + col, rightRow.get(col));
                                            }
                                            if (!combined.containsKey(col)) {
                                                combined.put(col, rightRow.get(col));
                                            }
                                        }
                                        nextJoinedRows.add(combined);
                                        matched = true;
                                    }
                                }
                                if (!matched) {
                                    Map<String, Object> combined = new HashMap<>(leftRow);
                                    for (String col : rightTd.columns) {
                                        combined.put(join.table + "." + col, null);
                                        if (join.alias != null) {
                                            combined.put(join.alias + "." + col, null);
                                        }
                                        if (!combined.containsKey(col)) {
                                            combined.put(col, null);
                                        }
                                    }
                                    nextJoinedRows.add(combined);
                                }
                            }
                        } else if ("RIGHT".equals(join.type)) {
                            for (Map<String, Object> rightRow : rightTd.rows) {
                                boolean matched = false;
                                for (Map<String, Object> leftRow : joinedRows) {
                                    if (joinConditionMatches(leftRow, rightRow, join)) {
                                        Map<String, Object> combined = new HashMap<>(leftRow);
                                        for (String col : rightTd.columns) {
                                            combined.put(join.table + "." + col, rightRow.get(col));
                                            if (join.alias != null) {
                                                combined.put(join.alias + "." + col, rightRow.get(col));
                                            }
                                            if (!combined.containsKey(col)) {
                                                combined.put(col, rightRow.get(col));
                                            }
                                        }
                                        nextJoinedRows.add(combined);
                                        matched = true;
                                    }
                                }
                                if (!matched) {
                                    Map<String, Object> combined = new HashMap<>();
                                    if (!joinedRows.isEmpty()) {
                                        for (String k : joinedRows.get(0).keySet()) {
                                            combined.put(k, null);
                                        }
                                    }
                                    for (String col : rightTd.columns) {
                                        combined.put(join.table + "." + col, rightRow.get(col));
                                        if (join.alias != null) {
                                            combined.put(join.alias + "." + col, rightRow.get(col));
                                        }
                                        combined.put(col, rightRow.get(col));
                                    }
                                    nextJoinedRows.add(combined);
                                }
                            }
                        }
                    }
                }
                joinedRows = nextJoinedRows;
            }
        }

        // 2. Filter rows using WHERE
        if (select.where != null) {
            if (joinedRows != null && !joinedRows.isEmpty()) {
                List<String> availCols = new ArrayList<>(joinedRows.get(0).keySet());
                validateWhereColumns(select.where, availCols);
            }
            List<Map<String, Object>> filtered = new ArrayList<>();
            String collation = resolveCollation(select.tableName, select.where.column);
            for (Map<String, Object> row : joinedRows) {
                if (select.where.evaluate(row, collation, this)) {
                    filtered.add(row);
                }
            }
            joinedRows = filtered;
        }

        // 3. Group by and Aggregation
        boolean hasAggregate = false;
        if (select.projection != null) {
            for (String item : select.projection) {
                if (isAggregate(item)) {
                    hasAggregate = true;
                    break;
                }
            }
        }

        if (select.groupBy != null || hasAggregate) {
            Map<Object, List<Map<String, Object>>> groups = new LinkedHashMap<>();
            if (select.groupBy != null) {
                List<String> gCols = select.groupBy.columns != null ? select.groupBy.columns : java.util.Collections.singletonList(select.groupBy.column);
                for (Map<String, Object> row : joinedRows) {
                    Object key;
                    if (gCols.size() == 1) {
                        key = getRowValueOrEvaluateWithAlias(row, gCols.get(0), select.aliases);
                        if (key == null) key = "NULL";
                    } else {
                        List<Object> compositeKey = new ArrayList<>();
                        for (String col : gCols) {
                            Object val = getRowValueOrEvaluateWithAlias(row, col, select.aliases);
                            compositeKey.add(val == null ? "NULL" : val);
                        }
                        key = compositeKey;
                    }
                    if (!groups.containsKey(key)) {
                        groups.put(key, new ArrayList<>());
                    }
                    groups.get(key).add(row);
                }
            } else {
                groups.put("ALL", joinedRows);
            }

            List<Map<String, Object>> aggregatedRows = new ArrayList<>();
            for (Map.Entry<Object, List<Map<String, Object>>> entry : groups.entrySet()) {
                List<Map<String, Object>> groupRows = entry.getValue();
                if (groupRows.isEmpty()) continue;

                Map<String, Object> aggRow = new HashMap<>();
                Map<String, Object> firstRow = groupRows.get(0);
                aggRow.putAll(firstRow);

                populateAggregatedRow(select, groupRows, aggRow);
                aggregatedRows.add(aggRow);
            }

            // Handle WITH ROLLUP summary rows
            if (select.groupBy != null && select.groupBy.withRollup && !joinedRows.isEmpty()) {
                List<String> gCols = select.groupBy.columns != null ? select.groupBy.columns : java.util.Collections.singletonList(select.groupBy.column);
                for (int prefixLen = gCols.size() - 1; prefixLen >= 0; prefixLen--) {
                    Map<Object, List<Map<String, Object>>> rollupGroups = new LinkedHashMap<>();
                    for (Map<String, Object> row : joinedRows) {
                        Object rKey;
                        if (prefixLen == 0) {
                            rKey = "ROLLUP_TOTAL";
                        } else if (prefixLen == 1) {
                            rKey = getRowValueOrEvaluateWithAlias(row, gCols.get(0), select.aliases);
                            if (rKey == null) rKey = "NULL";
                        } else {
                            List<Object> compositeKey = new ArrayList<>();
                            for (int i = 0; i < prefixLen; i++) {
                                Object val = getRowValueOrEvaluateWithAlias(row, gCols.get(i), select.aliases);
                                compositeKey.add(val == null ? "NULL" : val);
                            }
                            rKey = compositeKey;
                        }
                        if (!rollupGroups.containsKey(rKey)) {
                            rollupGroups.put(rKey, new ArrayList<>());
                        }
                        rollupGroups.get(rKey).add(row);
                    }

                    for (Map.Entry<Object, List<Map<String, Object>>> rEntry : rollupGroups.entrySet()) {
                        List<Map<String, Object>> rGroupRows = rEntry.getValue();
                        if (rGroupRows.isEmpty()) continue;

                        Map<String, Object> aggRow = new HashMap<>();
                        Map<String, Object> firstRow = rGroupRows.get(0);
                        aggRow.putAll(firstRow);

                        for (int i = prefixLen; i < gCols.size(); i++) {
                            String colName = gCols.get(i);
                            aggRow.put(colName, null);
                            if (select.tableName != null) {
                                aggRow.put(select.tableName + "." + colName, null);
                            }
                        }

                        populateAggregatedRow(select, rGroupRows, aggRow);
                        aggregatedRows.add(aggRow);
                    }
                }
            }

            joinedRows = aggregatedRows;
        }

        // 4. Having filter
        if (select.having != null) {
            List<Map<String, Object>> havingFiltered = new ArrayList<>();
            for (Map<String, Object> row : joinedRows) {
                if (select.having.evaluate(row, this)) {
                    havingFiltered.add(row);
                }
            }
            joinedRows = havingFiltered;
        }

        // Window function evaluation
        evaluateWindowFunctions(select, joinedRows);

        // 5. Sort
        if (select.orderBySpecs != null && !select.orderBySpecs.isEmpty()) {
            final List<Clause.OrderBy> specs = select.orderBySpecs;
            final int specsCount = specs.size();
            final String[] colTypes = new String[specsCount];
            final String[] collations = new String[specsCount];
            for (int i = 0; i < specsCount; i++) {
                Clause.OrderBy spec = specs.get(i);
                String type = "TEXT";
                if (select.tableName != null) {
                    try {
                        type = resolveColumnType(select, spec.column);
                    } catch (Exception e) {
                        type = "TEXT";
                    }
                }
                colTypes[i] = type;
                collations[i] = resolveCollation(select.tableName, spec.column);
            }

            Collections.sort(joinedRows, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> r1, Map<String, Object> r2) {
                    for (int i = 0; i < specsCount; i++) {
                        Clause.OrderBy spec = specs.get(i);
                        String orderByCol = spec.column;
                        boolean orderAsc = spec.asc;
                        String colType = colTypes[i];
                        String collation = collations[i];

                        Object v1 = getRowValueOrEvaluateWithAlias(r1, orderByCol, select.aliases);
                        Object v2 = getRowValueOrEvaluateWithAlias(r2, orderByCol, select.aliases);

                        if (v1 == null && v2 == null) continue;
                        if (v1 == null) return orderAsc ? -1 : 1;
                        if (v2 == null) return orderAsc ? 1 : -1;

                        int cmp = 0;
                        if (v1 instanceof Number && v2 instanceof Number) {
                            cmp = Double.compare(((Number) v1).doubleValue(), ((Number) v2).doubleValue());
                        } else if (colType.equalsIgnoreCase("INT") || colType.equalsIgnoreCase("INTEGER") || 
                                   colType.equalsIgnoreCase("DOUBLE") || colType.equalsIgnoreCase("DECIMAL")) {
                            try {
                                double d1 = Double.parseDouble(v1.toString());
                                double d2 = Double.parseDouble(v2.toString());
                                cmp = Double.compare(d1, d2);
                            } catch (NumberFormatException e) {
                                cmp = SqlCollation.compare(v1.toString(), v2.toString(), collation);
                            }
                        } else {
                            cmp = SqlCollation.compare(v1.toString(), v2.toString(), collation);
                        }

                        if (cmp != 0) {
                            return orderAsc ? cmp : -cmp;
                        }
                    }
                    return 0;
                }
            });
        }

        // 6. Project columns & apply aliases
        List<String> effectiveProjection = expandProjectionList(select);
        List<String> outColumns = new ArrayList<>();
        List<String> outTypes = new ArrayList<>();

        if (effectiveProjection == null) {
            if (select.tableName != null) {
                TableData baseTd = getOrLoadTable(select.tableName);
                if (select.joins == null || select.joins.isEmpty()) {
                    for (int i = 0; i < baseTd.columns.size(); i++) {
                        String col = baseTd.columns.get(i);
                        if (isColumnVisible(select.tableName, col)) {
                            outColumns.add(col);
                            outTypes.add(baseTd.types.get(i));
                        }
                    }
                } else {
                    for (int i = 0; i < baseTd.columns.size(); i++) {
                        String col = baseTd.columns.get(i);
                        if (isColumnVisible(select.tableName, col)) {
                            outColumns.add(select.tableName + "." + col);
                            outTypes.add(baseTd.types.get(i));
                        }
                    }
                    for (Clause.Join join : select.joins) {
                        TableData jtd = getOrLoadTable(join.table);
                        for (int i = 0; i < jtd.columns.size(); i++) {
                            String col = jtd.columns.get(i);
                            if (isColumnVisible(join.table, col)) {
                                outColumns.add(join.table + "." + col);
                                outTypes.add(jtd.types.get(i));
                            }
                        }
                    }
                }
            }
        } else {
            for (String col : effectiveProjection) {
                String type = "TEXT";
                if (isAggregate(col)) {
                    if (col.toUpperCase().startsWith("COUNT(")) {
                        type = "BIGINT";
                    } else {
                        String arg = col.substring(col.indexOf('(') + 1, col.lastIndexOf(')')).trim();
                        type = resolveColumnType(select, arg);
                        if (col.toUpperCase().startsWith("AVG(")) {
                            type = "DOUBLE";
                        }
                    }
                } else {
                    type = resolveColumnType(select, col);
                }

                String[] parts = splitAlias(col);
                String rawExpr = parts[0];
                String displayName = parts[1];
                if (parts[0].equals(parts[1]) && select.aliases != null) {
                    if (select.aliases.containsKey(col)) {
                        displayName = select.aliases.get(col);
                    } else {
                        String cleanCol = col.replaceAll("\\s+", "");
                        for (Map.Entry<String, String> e : select.aliases.entrySet()) {
                            if (e.getKey() != null && e.getKey().replaceAll("\\s+", "").equalsIgnoreCase(cleanCol)) {
                                displayName = e.getValue();
                                break;
                            }
                        }
                    }
                }
                displayName = getDisplayColumnName(displayName);
                outColumns.add(displayName);
                outTypes.add(type);
            }
        }

        List<Map<String, Object>> projectedRows = new ArrayList<>();
        for (Map<String, Object> row : joinedRows) {
            Map<String, Object> projRow = new HashMap<>();
            if (effectiveProjection == null) {
                for (String col : outColumns) {
                    Object val = getRowValue(row, col);
                    if (val == null) val = row.get(col);
                    projRow.put(col, val);
                }
            } else {
                for (int i = 0; i < effectiveProjection.size(); i++) {
                    String col = effectiveProjection.get(i);
                    String displayName = outColumns.get(i);
                    String[] parts = splitAlias(col);
                    String rawExpr = parts[0];

                    Object val = getRowValue(row, col);
                    if (val == null) val = getRowValue(row, rawExpr);
                    if (val == null) val = getRowValue(row, displayName);
                    if (val == null) val = row.get(displayName);
                    if (val == null) val = row.get(col);
                    if (val == null) val = row.get(rawExpr);

                    if (val == null && !row.containsKey(col) && !row.containsKey(rawExpr) && !row.containsKey(displayName)) {
                        val = SqlFunctions.evaluate(rawExpr, row, this);
                    }

                    projRow.put(displayName, val);
                    if (col != null && !col.equals(displayName)) {
                        projRow.put(col, val);
                    }
                }
            }
            projectedRows.add(projRow);
        }

        // 7. Distinct
        if (select.distinct) {
            List<Map<String, Object>> distinctRows = new ArrayList<>();
            Set<List<Object>> seen = new HashSet<>();
            for (Map<String, Object> r : projectedRows) {
                List<Object> signature = new ArrayList<>(outColumns.size());
                for (int i = 0; i < outColumns.size(); i++) {
                    String col = outColumns.get(i);
                    Object val = r.get(col);
                    String collation = resolveCollation(select.tableName, col);
                    signature.add(TableData.getCollationKey(val, collation));
                }
                if (seen.add(signature)) {
                    distinctRows.add(r);
                }
            }
            projectedRows = distinctRows;
        }

        // 8. Limit
        if (select.limit != null && select.limit >= 0 && select.limit < projectedRows.size()) {
            projectedRows = projectedRows.subList(0, select.limit);
        }

        // 9. Union
        if (select.union != null) {
            QueryResult currentResult = QueryResult.createSelectSuccess(outColumns, outTypes, projectedRows, 0);
            QueryResult unionResult = selectFrom(select.union.selectQuery);
            if (!unionResult.success) {
                return unionResult;
            }
            if (currentResult.columns.size() != unionResult.columns.size()) {
                throw new Exception("The used SELECT statements have a different number of columns");
            }

            List<Map<String, Object>> mergedRows = new ArrayList<>(currentResult.rows);
            if (!select.union.all) {
                Set<List<Object>> seen = new HashSet<>();
                // Populate seen with currentResult rows
                for (Map<String, Object> r : currentResult.rows) {
                    List<Object> signature = new ArrayList<>(outColumns.size());
                    for (int i = 0; i < outColumns.size(); i++) {
                        String col = outColumns.get(i);
                        Object val = r.get(col);
                        String collation = resolveCollation(select.tableName, col);
                        signature.add(TableData.getCollationKey(val, collation));
                    }
                    seen.add(signature);
                }

                for (Map<String, Object> unionRow : unionResult.rows) {
                    Map<String, Object> alignedRow = new HashMap<>();
                    List<Object> signature = new ArrayList<>(outColumns.size());
                    for (int i = 0; i < outColumns.size(); i++) {
                        String targetCol = outColumns.get(i);
                        String sourceCol = unionResult.columns.get(i);
                        Object val = unionRow.get(sourceCol);
                        alignedRow.put(targetCol, val);

                        String collation = resolveCollation(select.union.selectQuery.tableName, sourceCol);
                        signature.add(TableData.getCollationKey(val, collation));
                    }
                    if (seen.add(signature)) {
                        mergedRows.add(alignedRow);
                    }
                }
            } else {
                for (Map<String, Object> unionRow : unionResult.rows) {
                    Map<String, Object> alignedRow = new HashMap<>();
                    for (int i = 0; i < outColumns.size(); i++) {
                        String targetCol = outColumns.get(i);
                        String sourceCol = unionResult.columns.get(i);
                        alignedRow.put(targetCol, unionRow.get(sourceCol));
                    }
                    mergedRows.add(alignedRow);
                }
            }
            return QueryResult.createSelectSuccess(outColumns, outTypes, mergedRows, 0);
        }

        return QueryResult.createSelectSuccess(outColumns, outTypes, projectedRows, 0);
        } finally {
            for (String tmp : tempTablesCreated) {
                try {
                    dropTable(tmp, true);
                } catch (Exception ignored) {}
            }
        }
    }

    private void populateAggregatedRow(Command.Select select, List<Map<String, Object>> groupRows, Map<String, Object> aggRow) throws Exception {
        if (select.projection != null) {
            for (String projItem : select.projection) {
                List<int[]> aggMatches = findAggregateMatches(projItem);
                for (int[] m : aggMatches) {
                    String aggExpr = projItem.substring(m[0], m[1]);
                    aggRow.put(aggExpr, evaluateAggregate(aggExpr, groupRows));
                }
                if (isAggregate(projItem)) {
                    Object aggVal = evaluateAggregate(projItem, groupRows);
                    aggRow.put(projItem, aggVal);
                    int asIdx = projItem.toUpperCase().lastIndexOf(" AS ");
                    if (asIdx != -1) {
                        aggRow.put(projItem.substring(0, asIdx).trim(), aggVal);
                    }
                }
            }
        }
        if (select.having != null && select.having.aggregateFunc != null) {
            String havingFunc = select.having.aggregateFunc;
            aggRow.put(havingFunc, evaluateAggregate(havingFunc, groupRows));
        }
    }

    public static String[] splitAlias(String col) {
        if (col == null) return new String[]{"", ""};
        int len = col.length();
        int parenDepth = 0;
        int lastAsIdx = -1;
        for (int i = 0; i < len; i++) {
            char c = col.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') { if (parenDepth > 0) parenDepth--; }
            else if (parenDepth == 0 && i + 4 <= len) {
                if (" AS ".equalsIgnoreCase(col.substring(i, i + 4))) {
                    lastAsIdx = i;
                }
            }
        }
        if (lastAsIdx != -1) {
            String expr = col.substring(0, lastAsIdx).trim();
            String alias = col.substring(lastAsIdx + 4).trim();
            return new String[]{expr, alias};
        }
        return new String[]{col, col};
    }

    public QueryResult updateTable(String tableName, Map<String, Object> updates, Clause.Where where) throws Exception {
        return updateTable(tableName, updates, where, null, true, null);
    }

    public QueryResult updateTable(String tableName, Map<String, Object> updates, Clause.Where where, String orderByColumn, boolean orderAsc, Integer limit) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("UPDATE", activeDatabaseName, tableName);
        TableData td = getOrLoadTable(tableName);

        // Validate update column existence
        for (String col : updates.keySet()) {
            int idx = td.columns.indexOf(col);
            if (idx == -1) {
                throw new Exception("Error: Unknown column '" + col + "' in SET clause");
            }
        }
        if (where != null) {
            validateWhereColumns(where, td.columns);
        }

        // Identify columns that need auto-update on row changes
        List<String> autoUpdateCols = new ArrayList<>();
        ensureActiveSchema();
        JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
        if (tableSchema != null) {
            JSONObject onUpdateObj = tableSchema.optJSONObject("on_update");
            if (onUpdateObj != null) {
                Iterator<String> keys = onUpdateObj.keys();
                while (keys.hasNext()) {
                    String col = keys.next();
                    Object val = onUpdateObj.get(col);
                    if (val != JSONObject.NULL && val != null && SqlDefaults.isCurrentTimestampFunction(val.toString())) {
                        if (!updates.containsKey(col)) {
                            autoUpdateCols.add(col);
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> targetRows = new ArrayList<>(td.rows);
        if (orderByColumn != null && !orderByColumn.isEmpty()) {
            String colName = orderByColumn;
            boolean asc = orderAsc;
            Collections.sort(targetRows, (r1, r2) -> {
                Object v1 = getRowValue(r1, colName);
                Object v2 = getRowValue(r2, colName);
                int comp = compareValuesForSort(v1, v2);
                return asc ? comp : -comp;
            });
        }

        int affected = 0;
        String collation = (where != null) ? resolveCollation(where.column) : null;
        for (Map<String, Object> row : targetRows) {
            if (limit != null && affected >= limit) {
                break;
            }
            if (where == null || where.evaluate(row, collation, this)) {
                Map<String, Object> updatedRow = new HashMap<>(row);
                for (Map.Entry<String, Object> entry : updates.entrySet()) {
                    String col = entry.getKey();
                    int idx = td.columns.indexOf(col);
                    String type = td.types.get(idx);
                    Object rawVal = entry.getValue();
                    Object valToAssign;
                    if (rawVal instanceof String && ((String) rawVal).startsWith("\u0000EXPR\u0000")) {
                        String exprStr = ((String) rawVal).substring("\u0000EXPR\u0000".length());
                        valToAssign = SqlFunctions.evaluate(exprStr, row, this);
                    } else if ("\u0000DEFAULT\u0000".equals(rawVal)) {
                        valToAssign = getDefaultValue(tableName, col);
                    } else {
                        valToAssign = rawVal;
                    }
                    Object validated = validateAndConvertType(col, valToAssign, type);
                    updatedRow.put(col, validated);
                }
                if (!autoUpdateCols.isEmpty()) {
                    String currentTs = SqlDefaults.getCurrentTimestampString();
                    for (String col : autoUpdateCols) {
                        updatedRow.put(col, currentTs);
                    }
                }

                validateRowConstraints(tableName, updatedRow, td, row);

                for (Map.Entry<String, Object> entry : updatedRow.entrySet()) {
                    row.put(entry.getKey(), entry.getValue());
                }
                affected++;
            }
        }

        if (affected > 0) {
            td.isDirty = true;
            if (!deferWrite) {
                storageEngine.writeTableRows(activeDatabaseName, tableName, td.toJSONArray());
                td.isDirty = false;
            }
        }

        return QueryResult.createSuccess("Query OK, " + affected + " row" + (affected == 1 ? "" : "s") + " affected", affected, 0);
    }

    public QueryResult deleteFrom(String tableName, Clause.Where where) throws Exception {
        return deleteFrom(tableName, where, null, true, null);
    }

    public QueryResult deleteFrom(String tableName, Clause.Where where, String orderByColumn, boolean orderAsc, Integer limit) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("DELETE", activeDatabaseName, tableName);
        TableData td = getOrLoadTable(tableName);

        List<Map<String, Object>> targetRows = new ArrayList<>(td.rows);
        if (orderByColumn != null && !orderByColumn.isEmpty()) {
            String colName = orderByColumn;
            boolean asc = orderAsc;
            Collections.sort(targetRows, (r1, r2) -> {
                Object v1 = getRowValue(r1, colName);
                Object v2 = getRowValue(r2, colName);
                int comp = compareValuesForSort(v1, v2);
                return asc ? comp : -comp;
            });
        }

        int affected = 0;
        String collation = (where != null) ? resolveCollation(where.column) : null;
        for (Map<String, Object> row : targetRows) {
            if (limit != null && affected >= limit) {
                break;
            }
            if (where == null || where.evaluate(row, collation, this)) {
                td.rows.remove(row);
                affected++;
            }
        }

        if (affected > 0) {
            td.isDirty = true;
            if (!deferWrite) {
                storageEngine.writeTableRows(activeDatabaseName, tableName, td.toJSONArray());
                td.isDirty = false;
            }
        }

        return QueryResult.createSuccess("Query OK, " + affected + " row" + (affected == 1 ? "" : "s") + " affected", affected, 0);
    }

    public void validateWhereColumns(Clause.Where where, List<String> validColumns) throws Exception {
        if (where == null || validColumns == null || validColumns.isEmpty()) return;
        if ("EXISTS".equalsIgnoreCase(where.operator) || "NOT EXISTS".equalsIgnoreCase(where.operator)) {
            return;
        }
        if (where.logicalOperator != null && where.subConditions != null) {
            for (Clause.Where sub : where.subConditions) {
                validateWhereColumns(sub, validColumns);
            }
            return;
        }
        if (where.column != null && !where.column.isEmpty()) {
            validateSingleWhereColumn(where.column, validColumns);
        }
        if (where.isValueColumn && where.value instanceof String) {
            String valStr = (String) where.value;
            if (!valStr.startsWith("@") && !valStr.contains("(")) {
                validateSingleWhereColumn(valStr, validColumns);
            }
        }
    }

    private void validateSingleWhereColumn(String colExpr, List<String> validColumns) throws Exception {
        if (colExpr == null || colExpr.startsWith("@")) {
            return;
        }
        List<String> extractedCols = new ArrayList<>();
        try {
            SqlFunctions.Expression expr = SqlFunctions.parse(colExpr);
            expr.collectColumns(extractedCols);
        } catch (Exception e) {
            extractedCols.add(colExpr);
        }

        for (String col : extractedCols) {
            if (col.startsWith("@") || "*".equals(col)) continue;
            String cleanCol = col;
            int lastDot = cleanCol.lastIndexOf('.');
            if (lastDot != -1) {
                cleanCol = cleanCol.substring(lastDot + 1);
            }
            cleanCol = cleanCol.trim();
            if (cleanCol.isEmpty()) continue;

            boolean found = false;
            for (String valid : validColumns) {
                String cleanValid = valid;
                int dotIdx = cleanValid.lastIndexOf('.');
                if (dotIdx != -1) {
                    cleanValid = cleanValid.substring(dotIdx + 1);
                }
                if (cleanValid.equalsIgnoreCase(cleanCol)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new Exception("ERROR 1054 (42S22): Unknown column '" + colExpr + "' in 'where clause'");
            }
        }
    }

    public QueryResult createUser(String username, String host, String password) throws Exception {
        return privilegeManager.createUser(username, host, password);
    }

    public QueryResult dropUser(String username, String host) throws Exception {
        return privilegeManager.dropUser(username, host, false);
    }

    public QueryResult dropUser(String username, String host, boolean ifExists) throws Exception {
        return privilegeManager.dropUser(username, host, ifExists);
    }

    public QueryResult grantPrivileges(List<String> privileges, String dbPattern, String username, String host) throws Exception {
        return privilegeManager.grantPrivileges(privileges, dbPattern, username, host);
    }

    public QueryResult flushPrivileges() throws Exception {
        return privilegeManager.flushPrivileges();
    }

    private void insertAtPosition(JSONArray arr, Object val, String pos, String target, JSONArray colsArr) throws Exception {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.get(i));
        }
        int idx = list.size(); // default append
        if ("FIRST".equalsIgnoreCase(pos)) {
            idx = 0;
        } else if ("AFTER".equalsIgnoreCase(pos) && target != null) {
            int targetIdx = -1;
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(target)) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx == -1) {
                throw new Exception("Error: Target column '" + target + "' does not exist");
            }
            idx = targetIdx + 1;
        }
        list.add(idx, val);
        
        while (arr.length() > 0) {
            arr.remove(0);
        }
        for (Object item : list) {
            arr.put(item);
        }
    }

    private void moveColumnPosition(JSONArray colsArr, JSONArray typesArr, String colName, String pos, String target) throws Exception {
        int oldIdx = -1;
        for (int i = 0; i < colsArr.length(); i++) {
            if (colsArr.getString(i).equalsIgnoreCase(colName)) {
                oldIdx = i;
                break;
            }
        }
        if (oldIdx == -1) return;
        
        String colVal = colsArr.getString(oldIdx);
        String typeVal = typesArr.getString(oldIdx);
        
        colsArr.remove(oldIdx);
        typesArr.remove(oldIdx);
        
        List<String> cols = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (int i = 0; i < colsArr.length(); i++) {
            cols.add(colsArr.getString(i));
            types.add(typesArr.getString(i));
        }
        
        int idx = cols.size();
        if ("FIRST".equalsIgnoreCase(pos)) {
            idx = 0;
        } else if ("AFTER".equalsIgnoreCase(pos) && target != null) {
            int targetIdx = -1;
            for (int i = 0; i < cols.size(); i++) {
                if (cols.get(i).equalsIgnoreCase(target)) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx == -1) {
                throw new Exception("Error: Target column '" + target + "' does not exist");
            }
            idx = targetIdx + 1;
        }
        
        cols.add(idx, colVal);
        types.add(idx, typeVal);
        
        while (colsArr.length() > 0) {
            colsArr.remove(0);
        }
        while (typesArr.length() > 0) {
            typesArr.remove(0);
        }
        for (String c : cols) colsArr.put(c);
        for (String t : types) typesArr.put(t);
    }

    private void renameAndSetKey(JSONObject parent, String fieldName, String oldKey, String newKey, Object newVal) throws Exception {
        JSONObject obj = parent.optJSONObject(fieldName);
        if (obj != null) {
            removeKey(parent, fieldName, oldKey);
            obj.put(newKey, newVal == null ? JSONObject.NULL : newVal);
        }
    }

    private void removeKey(JSONObject parent, String fieldName, String key) {
        JSONObject obj = parent.optJSONObject(fieldName);
        if (obj != null) {
            Iterator<String> iks = obj.keys();
            List<String> toRemove = new ArrayList<>();
            while (iks.hasNext()) {
                String k = iks.next();
                if (k.equalsIgnoreCase(key)) {
                    toRemove.add(k);
                }
            }
            for (String r : toRemove) {
                obj.remove(r);
            }
        }
    }

    public QueryResult alterTable(Command.AlterTable cmd) throws Exception {
        String tableName = resolveTableName(cmd.tableName);
        verifyPrivilege("ALTER", activeDatabaseName, tableName);
        ensureActiveSchema();
        if (!activeSchemaJson.has(tableName)) {
            throw new Exception("Error: Table '" + cmd.tableName + "' does not exist");
        }

        if ("RENAME_TABLE".equals(cmd.operation)) {
            return renameTable(tableName, cmd.renameToTable);
        }

        JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
        JSONArray colsArr = tableSchema.getJSONArray("columns");
        JSONArray typesArr = tableSchema.getJSONArray("types");

        if ("ADD_COLUMN".equals(cmd.operation)) {
            Command.ColumnDef cd = cmd.columnDef;
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(cd.name)) {
                    throw new Exception("Error: Duplicate column name '" + cd.name + "'");
                }
            }
            insertAtPosition(colsArr, cd.name, cmd.position, cmd.targetColumn, colsArr);
            insertAtPosition(typesArr, cd.type.toUpperCase(), cmd.position, cmd.targetColumn, colsArr);
            
            JSONObject defaults = tableSchema.optJSONObject("defaults");
            if (defaults != null) {
                defaults.put(cd.name, cd.defaultValue == null ? JSONObject.NULL : cd.defaultValue);
            }
            JSONObject nullables = tableSchema.optJSONObject("nullables");
            if (nullables != null) {
                nullables.put(cd.name, cd.nullable);
            }
            JSONObject keys = tableSchema.optJSONObject("keys");
            if (keys != null) {
                keys.put(cd.name, cd.isPrimaryKey ? "PRI" : (cd.isUnique ? "UNI" : ""));
            }
            JSONObject extras = tableSchema.optJSONObject("extras");
            if (extras != null) {
                if (cd.isAutoIncrement) {
                    extras.put(cd.name, "auto_increment");
                } else if (cd.onUpdateValue != null && SqlDefaults.isCurrentTimestampFunction(cd.onUpdateValue)) {
                    if (SqlDefaults.isCurrentTimestampFunction(cd.defaultValue)) {
                        extras.put(cd.name, "DEFAULT_GENERATED on update " + cd.onUpdateValue.toUpperCase());
                    } else {
                        extras.put(cd.name, "on update " + cd.onUpdateValue.toUpperCase());
                    }
                } else {
                    extras.put(cd.name, "");
                }
            }

            JSONObject onUpdate = tableSchema.optJSONObject("on_update");
            if (onUpdate == null) {
                onUpdate = new JSONObject();
                tableSchema.put("on_update", onUpdate);
            }
            onUpdate.put(cd.name, cd.onUpdateValue == null ? JSONObject.NULL : cd.onUpdateValue);

            JSONObject attributes = tableSchema.optJSONObject("attributes");
            if (attributes == null) {
                attributes = new JSONObject();
                tableSchema.put("attributes", attributes);
            }
            if (cd.attributes != null) {
                attributes.put(cd.name, cd.attributes.toJsonObject());
            } else {
                attributes.put(cd.name, new JSONObject());
            }
            
            if (cd.isPrimaryKey) {
                JSONArray pkArr = tableSchema.optJSONArray("primary_key");
                if (pkArr == null) {
                    pkArr = new JSONArray();
                    tableSchema.put("primary_key", pkArr);
                }
                pkArr.put(cd.name);
            }
            if (cd.isUnique) {
                JSONArray uniques = tableSchema.optJSONArray("uniques");
                if (uniques == null) {
                    uniques = new JSONArray();
                    tableSchema.put("uniques", uniques);
                }
                JSONArray singleGroup = new JSONArray();
                singleGroup.put(cd.name);
                uniques.put(singleGroup);
            }
        } else if ("MODIFY_COLUMN".equals(cmd.operation)) {
            Command.ColumnDef cd = cmd.columnDef;
            int idx = -1;
            String targetCol = cd.name;
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(cd.name)) {
                    idx = i;
                    targetCol = colsArr.getString(i);
                    break;
                }
            }
            if (idx == -1) {
                throw new Exception("Error: Column '" + cd.name + "' does not exist");
            }
            typesArr.put(idx, cd.type.toUpperCase());
            
            removeKey(tableSchema, "defaults", targetCol);
            removeKey(tableSchema, "nullables", targetCol);
            removeKey(tableSchema, "keys", targetCol);
            removeKey(tableSchema, "extras", targetCol);
            removeKey(tableSchema, "on_update", targetCol);
            removeKey(tableSchema, "attributes", targetCol);

            JSONObject defaults = tableSchema.optJSONObject("defaults");
            if (defaults == null) {
                defaults = new JSONObject();
                tableSchema.put("defaults", defaults);
            }
            defaults.put(targetCol, cd.defaultValue == null ? JSONObject.NULL : cd.defaultValue);

            JSONObject nullables = tableSchema.optJSONObject("nullables");
            if (nullables == null) {
                nullables = new JSONObject();
                tableSchema.put("nullables", nullables);
            }
            nullables.put(targetCol, cd.nullable);

            JSONObject keys = tableSchema.optJSONObject("keys");
            if (keys == null) {
                keys = new JSONObject();
                tableSchema.put("keys", keys);
            }
            if (cd.isPrimaryKey) {
                keys.put(targetCol, "PRI");
            } else if (cd.isUnique) {
                keys.put(targetCol, "UNI");
            } else {
                JSONArray pkArr = tableSchema.optJSONArray("primary_key");
                boolean isPk = false;
                if (pkArr != null) {
                    for (int p = 0; p < pkArr.length(); p++) {
                        if (pkArr.getString(p).equalsIgnoreCase(targetCol)) {
                            isPk = true;
                            break;
                        }
                    }
                }
                if (isPk) {
                    keys.put(targetCol, "PRI");
                } else {
                    keys.put(targetCol, "");
                }
            }

            JSONObject extras = tableSchema.optJSONObject("extras");
            if (extras == null) {
                extras = new JSONObject();
                tableSchema.put("extras", extras);
            }
            if (cd.isAutoIncrement) {
                extras.put(targetCol, "auto_increment");
                if (!tableSchema.has("auto_increment")) {
                    tableSchema.put("auto_increment", 1L);
                }
            } else if (cd.onUpdateValue != null && SqlDefaults.isCurrentTimestampFunction(cd.onUpdateValue)) {
                if (SqlDefaults.isCurrentTimestampFunction(cd.defaultValue)) {
                    extras.put(targetCol, "DEFAULT_GENERATED on update " + cd.onUpdateValue.toUpperCase());
                } else {
                    extras.put(targetCol, "on update " + cd.onUpdateValue.toUpperCase());
                }
            } else {
                extras.put(targetCol, "");
            }

            JSONObject onUpdate = tableSchema.optJSONObject("on_update");
            if (onUpdate == null) {
                onUpdate = new JSONObject();
                tableSchema.put("on_update", onUpdate);
            }
            onUpdate.put(targetCol, cd.onUpdateValue == null ? JSONObject.NULL : cd.onUpdateValue);

            JSONObject attributes = tableSchema.optJSONObject("attributes");
            if (attributes == null) {
                attributes = new JSONObject();
                tableSchema.put("attributes", attributes);
            }
            if (cd.attributes != null) {
                attributes.put(targetCol, cd.attributes.toJsonObject());
            } else {
                attributes.put(targetCol, new JSONObject());
            }

            if (cmd.position != null) {
                moveColumnPosition(colsArr, typesArr, targetCol, cmd.position, cmd.targetColumn);
            }
        } else if ("CHANGE_COLUMN".equals(cmd.operation)) {
            String oldCol = cmd.columnName;
            Command.ColumnDef cd = cmd.columnDef;
            int idx = -1;
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(oldCol)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new Exception("Error: Column '" + oldCol + "' does not exist");
            }
            if (!oldCol.equalsIgnoreCase(cd.name)) {
                for (int i = 0; i < colsArr.length(); i++) {
                    if (colsArr.getString(i).equalsIgnoreCase(cd.name)) {
                        throw new Exception("Error: Duplicate column name '" + cd.name + "'");
                    }
                }
            }
            colsArr.put(idx, cd.name);
            typesArr.put(idx, cd.type.toUpperCase());
            
            renameAndSetKey(tableSchema, "defaults", oldCol, cd.name, cd.defaultValue == null ? JSONObject.NULL : cd.defaultValue);
            renameAndSetKey(tableSchema, "nullables", oldCol, cd.name, cd.nullable);
            renameAndSetKey(tableSchema, "keys", oldCol, cd.name, cd.isPrimaryKey ? "PRI" : (cd.isUnique ? "UNI" : ""));
            
            String extraVal = "";
            if (cd.isAutoIncrement) {
                extraVal = "auto_increment";
            } else if (cd.onUpdateValue != null && SqlDefaults.isCurrentTimestampFunction(cd.onUpdateValue)) {
                if (SqlDefaults.isCurrentTimestampFunction(cd.defaultValue)) {
                    extraVal = "DEFAULT_GENERATED on update " + cd.onUpdateValue.toUpperCase();
                } else {
                    extraVal = "on update " + cd.onUpdateValue.toUpperCase();
                }
            }
            renameAndSetKey(tableSchema, "extras", oldCol, cd.name, extraVal);
            
            renameAndSetKey(tableSchema, "on_update", oldCol, cd.name, cd.onUpdateValue == null ? JSONObject.NULL : cd.onUpdateValue);

            JSONObject attributes = tableSchema.optJSONObject("attributes");
            if (attributes == null) {
                attributes = new JSONObject();
                tableSchema.put("attributes", attributes);
            }
            attributes.remove(oldCol);
            if (cd.attributes != null) {
                attributes.put(cd.name, cd.attributes.toJsonObject());
            } else {
                attributes.put(cd.name, new JSONObject());
            }
            
            JSONArray pkArr = tableSchema.optJSONArray("primary_key");
            if (pkArr != null) {
                for (int i = 0; i < pkArr.length(); i++) {
                    if (pkArr.getString(i).equalsIgnoreCase(oldCol)) {
                        pkArr.put(i, cd.name);
                    }
                }
            }
            JSONArray uniques = tableSchema.optJSONArray("uniques");
            if (uniques != null) {
                for (int i = 0; i < uniques.length(); i++) {
                    JSONArray group = uniques.getJSONArray(i);
                    for (int j = 0; j < group.length(); j++) {
                        if (group.getString(j).equalsIgnoreCase(oldCol)) {
                            group.put(j, cd.name);
                        }
                    }
                }
            }
            JSONObject fks = tableSchema.optJSONObject("foreign_keys");
            if (fks != null && fks.has(oldCol)) {
                Object ref = fks.get(oldCol);
                fks.remove(oldCol);
                fks.put(cd.name, ref);
            }
            JSONArray checks = tableSchema.optJSONArray("checks");
            if (checks != null) {
                for (int i = 0; i < checks.length(); i++) {
                    JSONObject chk = checks.getJSONObject(i);
                    if (chk.optString("column").equalsIgnoreCase(oldCol)) {
                        chk.put("column", cd.name);
                    }
                }
            }
            if (cmd.position != null) {
                moveColumnPosition(colsArr, typesArr, cd.name, cmd.position, cmd.targetColumn);
            }
        } else if ("RENAME_COLUMN".equals(cmd.operation)) {
            String oldCol = cmd.columnName;
            String newCol = cmd.newColumnName;
            int idx = -1;
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(oldCol)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new Exception("Error: Column '" + oldCol + "' does not exist");
            }
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(newCol)) {
                    throw new Exception("Error: Duplicate column name '" + newCol + "'");
                }
            }
            colsArr.put(idx, newCol);
            
            renameAndSetKey(tableSchema, "defaults", oldCol, newCol, tableSchema.optJSONObject("defaults") != null ? tableSchema.optJSONObject("defaults").opt(oldCol) : JSONObject.NULL);
            renameAndSetKey(tableSchema, "nullables", oldCol, newCol, tableSchema.optJSONObject("nullables") != null ? tableSchema.optJSONObject("nullables").opt(oldCol) : true);
            renameAndSetKey(tableSchema, "keys", oldCol, newCol, tableSchema.optJSONObject("keys") != null ? tableSchema.optJSONObject("keys").opt(oldCol) : "");
            renameAndSetKey(tableSchema, "extras", oldCol, newCol, tableSchema.optJSONObject("extras") != null ? tableSchema.optJSONObject("extras").opt(oldCol) : "");
            renameAndSetKey(tableSchema, "on_update", oldCol, newCol, tableSchema.optJSONObject("on_update") != null ? tableSchema.optJSONObject("on_update").opt(oldCol) : JSONObject.NULL);
            renameAndSetKey(tableSchema, "attributes", oldCol, newCol, tableSchema.optJSONObject("attributes") != null ? tableSchema.optJSONObject("attributes").opt(oldCol) : new JSONObject());

            JSONArray pkArr = tableSchema.optJSONArray("primary_key");
            if (pkArr != null) {
                for (int i = 0; i < pkArr.length(); i++) {
                    if (pkArr.getString(i).equalsIgnoreCase(oldCol)) {
                        pkArr.put(i, newCol);
                    }
                }
            }
            JSONArray uniques = tableSchema.optJSONArray("uniques");
            if (uniques != null) {
                for (int i = 0; i < uniques.length(); i++) {
                    JSONArray group = uniques.getJSONArray(i);
                    for (int j = 0; j < group.length(); j++) {
                        if (group.getString(j).equalsIgnoreCase(oldCol)) {
                            group.put(j, newCol);
                        }
                    }
                }
            }
            JSONObject fks = tableSchema.optJSONObject("foreign_keys");
            if (fks != null && fks.has(oldCol)) {
                Object ref = fks.get(oldCol);
                fks.remove(oldCol);
                fks.put(newCol, ref);
            }
            JSONArray checks = tableSchema.optJSONArray("checks");
            if (checks != null) {
                for (int i = 0; i < checks.length(); i++) {
                    JSONObject chk = checks.getJSONObject(i);
                    if (chk.optString("column").equalsIgnoreCase(oldCol)) {
                        chk.put("column", newCol);
                    }
                }
            }
        } else if ("DROP_COLUMN".equals(cmd.operation)) {
            String col = cmd.columnName;
            int idx = -1;
            for (int i = 0; i < colsArr.length(); i++) {
                if (colsArr.getString(i).equalsIgnoreCase(col)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new Exception("Error: Column '" + col + "' does not exist");
            }
            colsArr.remove(idx);
            typesArr.remove(idx);
            
            removeKey(tableSchema, "defaults", col);
            removeKey(tableSchema, "nullables", col);
            removeKey(tableSchema, "keys", col);
            removeKey(tableSchema, "extras", col);
            removeKey(tableSchema, "on_update", col);
            removeKey(tableSchema, "attributes", col);

            JSONArray pkArr = tableSchema.optJSONArray("primary_key");
            if (pkArr != null) {
                for (int i = pkArr.length() - 1; i >= 0; i--) {
                    if (pkArr.getString(i).equalsIgnoreCase(col)) {
                        pkArr.remove(i);
                    }
                }
            }
            JSONArray uniques = tableSchema.optJSONArray("uniques");
            if (uniques != null) {
                for (int i = uniques.length() - 1; i >= 0; i--) {
                    JSONArray group = uniques.getJSONArray(i);
                    for (int j = group.length() - 1; j >= 0; j--) {
                        if (group.getString(j).equalsIgnoreCase(col)) {
                            group.remove(j);
                        }
                    }
                    if (group.length() == 0) {
                        uniques.remove(i);
                    }
                }
            }
            JSONObject fks = tableSchema.optJSONObject("foreign_keys");
            if (fks != null) {
                fks.remove(col);
            }
            JSONArray checks = tableSchema.optJSONArray("checks");
            if (checks != null) {
                for (int i = checks.length() - 1; i >= 0; i--) {
                    JSONObject chk = checks.getJSONObject(i);
                    if (chk.optString("column").equalsIgnoreCase(col)) {
                        checks.remove(i);
                    }
                }
            }
        } else if ("ADD_PRIMARY_KEY".equals(cmd.operation)) {
            JSONArray existingPk = tableSchema.optJSONArray("primary_key");
            if (existingPk != null && existingPk.length() > 0) {
                throw new Exception("Error: Multiple primary key defined");
            }
            List<String> resolvedCols = new ArrayList<>();
            for (String rawCol : cmd.columnsList) {
                String match = null;
                for (int cIdx = 0; cIdx < colsArr.length(); cIdx++) {
                    if (colsArr.getString(cIdx).equalsIgnoreCase(rawCol)) {
                        match = colsArr.getString(cIdx);
                        break;
                    }
                }
                if (match == null) {
                    throw new Exception("Error: Key column '" + rawCol + "' doesn't exist in table");
                }
                resolvedCols.add(match);
            }
            JSONArray pkArr = new JSONArray();
            JSONObject keys = tableSchema.optJSONObject("keys");
            if (keys == null) {
                keys = new JSONObject();
                tableSchema.put("keys", keys);
            }
            JSONObject nullables = tableSchema.optJSONObject("nullables");
            if (nullables == null) {
                nullables = new JSONObject();
                tableSchema.put("nullables", nullables);
            }
            for (String col : resolvedCols) {
                pkArr.put(col);
                keys.put(col, "PRI");
                nullables.put(col, false);
            }
            tableSchema.put("primary_key", pkArr);
            JSONObject indexesObj = tableSchema.optJSONObject("indexes");
            if (indexesObj == null) {
                indexesObj = new JSONObject();
                tableSchema.put("indexes", indexesObj);
            }
            JSONObject idxMeta = new JSONObject();
            idxMeta.put("name", "PRIMARY");
            idxMeta.put("columns", pkArr);
            idxMeta.put("unique", true);
            idxMeta.put("type", "BTREE");
            indexesObj.put("PRIMARY", idxMeta);
        } else if ("DROP_PRIMARY_KEY".equals(cmd.operation)) {
            JSONArray pkArr = tableSchema.optJSONArray("primary_key");
            if (pkArr == null || pkArr.length() == 0) {
                throw new Exception("Error: Can't DROP 'PRIMARY'; check that column/key exists");
            }
            JSONObject keys = tableSchema.optJSONObject("keys");
            for (int i = 0; i < pkArr.length(); i++) {
                String col = pkArr.getString(i);
                if (keys != null && "PRI".equals(keys.optString(col))) {
                    keys.put(col, "");
                }
            }
            tableSchema.remove("primary_key");
            JSONObject indexesObj = tableSchema.optJSONObject("indexes");
            if (indexesObj != null) {
                indexesObj.remove("PRIMARY");
            }
        } else if ("ADD_FOREIGN_KEY".equals(cmd.operation)) {
            JSONObject fks = tableSchema.optJSONObject("foreign_keys");
            if (fks == null) {
                fks = new JSONObject();
                tableSchema.put("foreign_keys", fks);
            }
            for (int i = 0; i < cmd.columnsList.size(); i++) {
                String rawChildCol = cmd.columnsList.get(i);
                String childCol = null;
                for (int cIdx = 0; cIdx < colsArr.length(); cIdx++) {
                    if (colsArr.getString(cIdx).equalsIgnoreCase(rawChildCol)) {
                        childCol = colsArr.getString(cIdx);
                        break;
                    }
                }
                if (childCol == null) {
                    throw new Exception("Error: Key column '" + rawChildCol + "' doesn't exist in table");
                }
                String parentCol = cmd.referenceColumns.get(i);
                fks.put(childCol, cmd.referenceTable + "." + parentCol);
            }
        } else if ("DROP_FOREIGN_KEY".equals(cmd.operation)) {
            JSONObject fks = tableSchema.optJSONObject("foreign_keys");
            if (fks == null || !fks.has(cmd.constraintName)) {
                throw new Exception("Error: Foreign key '" + cmd.constraintName + "' does not exist");
            }
            fks.remove(cmd.constraintName);
        } else if ("ADD_UNIQUE".equals(cmd.operation) || "ADD_INDEX".equals(cmd.operation)) {
            boolean isUnique = "ADD_UNIQUE".equals(cmd.operation);
            List<String> resolvedCols = new ArrayList<>();
            for (String rawCol : cmd.columnsList) {
                String match = null;
                for (int cIdx = 0; cIdx < colsArr.length(); cIdx++) {
                    if (colsArr.getString(cIdx).equalsIgnoreCase(rawCol)) {
                        match = colsArr.getString(cIdx);
                        break;
                    }
                }
                if (match == null) {
                    throw new Exception("Error: Key column '" + rawCol + "' doesn't exist in table");
                }
                resolvedCols.add(match);
            }
            JSONObject indexesObj = tableSchema.optJSONObject("indexes");
            if (indexesObj == null) {
                indexesObj = new JSONObject();
                tableSchema.put("indexes", indexesObj);
            }

            String idxName = cmd.constraintName;
            if (idxName == null) {
                idxName = isUnique ? "uq_" + String.join("_", resolvedCols) : "idx_" + (resolvedCols.isEmpty() ? "col" : resolvedCols.get(0));
            }
            Iterator<String> iks = indexesObj.keys();
            while (iks.hasNext()) {
                if (iks.next().equalsIgnoreCase(idxName)) {
                    throw new Exception("Error: Duplicate key name '" + idxName + "'");
                }
            }

            if (isUnique) {
                JSONArray uniques = tableSchema.optJSONArray("uniques");
                if (uniques == null) {
                    uniques = new JSONArray();
                    tableSchema.put("uniques", uniques);
                }
                JSONArray group = new JSONArray();
                JSONObject keys = tableSchema.optJSONObject("keys");
                if (keys == null) {
                    keys = new JSONObject();
                    tableSchema.put("keys", keys);
                }
                for (String col : resolvedCols) {
                    group.put(col);
                    keys.put(col, "UNI");
                }
                uniques.put(group);
            }

            JSONObject indexMeta = new JSONObject();
            indexMeta.put("name", idxName);
            indexMeta.put("columns", new JSONArray(resolvedCols));
            indexMeta.put("unique", isUnique);
            indexMeta.put("type", cmd.indexType != null ? cmd.indexType : "BTREE");
            indexesObj.put(idxName, indexMeta);
        } else if ("DROP_INDEX".equals(cmd.operation)) {
            JSONArray uniques = tableSchema.optJSONArray("uniques");
            JSONObject indexesObj = tableSchema.optJSONObject("indexes");
            boolean dropped = false;
            String matchedIndexName = null;
            if (indexesObj != null) {
                Iterator<String> iks = indexesObj.keys();
                while (iks.hasNext()) {
                    String k = iks.next();
                    if (k.equalsIgnoreCase(cmd.constraintName)) {
                        matchedIndexName = k;
                        break;
                    }
                }
            }
            if (indexesObj != null && matchedIndexName != null) {
                JSONObject idxMeta = indexesObj.getJSONObject(matchedIndexName);
                JSONArray cols = idxMeta.optJSONArray("columns");
                if (cols != null && uniques != null) {
                    for (int i = uniques.length() - 1; i >= 0; i--) {
                        JSONArray group = uniques.getJSONArray(i);
                        if (group.length() == cols.length()) {
                            boolean groupMatch = true;
                            for (int j = 0; j < group.length(); j++) {
                                if (!group.getString(j).equalsIgnoreCase(cols.getString(j))) {
                                    groupMatch = false;
                                    break;
                                }
                            }
                            if (groupMatch) {
                                JSONObject keys = tableSchema.optJSONObject("keys");
                                if (keys != null) {
                                    for (int j = 0; j < group.length(); j++) {
                                        keys.put(group.getString(j), "");
                                    }
                                }
                                uniques.remove(i);
                            }
                        }
                    }
                }
                indexesObj.remove(matchedIndexName);
                dropped = true;
            } else {
                if (uniques != null) {
                    for (int i = uniques.length() - 1; i >= 0; i--) {
                        JSONArray group = uniques.getJSONArray(i);
                        boolean matches = false;
                        for (int j = 0; j < group.length(); j++) {
                            String col = group.getString(j);
                            if (col.equalsIgnoreCase(cmd.constraintName) ||
                                cmd.constraintName.toLowerCase().startsWith(col.toLowerCase()) ||
                                cmd.constraintName.toLowerCase().contains(col.toLowerCase())) {
                                matches = true;
                                break;
                            }
                        }
                        if (matches) {
                            JSONObject keys = tableSchema.optJSONObject("keys");
                            if (keys != null) {
                                for (int j = 0; j < group.length(); j++) {
                                    keys.put(group.getString(j), "");
                                }
                            }
                            uniques.remove(i);
                            dropped = true;
                        }
                    }
                }
            }
            if (!dropped) {
                throw new Exception("Error: Index '" + cmd.constraintName + "' does not exist");
            }
        } else if ("DEFAULT_VALUE_CHANGE".equals(cmd.operation)) {
            JSONObject defaults = tableSchema.optJSONObject("defaults");
            String newDefVal = null;
            if (defaults != null) {
                if (cmd.dropDefault) {
                    defaults.put(cmd.columnName, JSONObject.NULL);
                } else {
                    defaults.put(cmd.columnName, cmd.defaultValue);
                    newDefVal = cmd.defaultValue;
                }
            }
            JSONObject onUpdate = tableSchema.optJSONObject("on_update");
            JSONObject extras = tableSchema.optJSONObject("extras");
            if (onUpdate != null && extras != null && onUpdate.has(cmd.columnName)) {
                Object ouVal = onUpdate.get(cmd.columnName);
                if (ouVal != JSONObject.NULL && ouVal != null && SqlDefaults.isCurrentTimestampFunction(ouVal.toString())) {
                    if (SqlDefaults.isCurrentTimestampFunction(newDefVal)) {
                        extras.put(cmd.columnName, "DEFAULT_GENERATED on update " + ouVal.toString().toUpperCase());
                    } else {
                        extras.put(cmd.columnName, "on update " + ouVal.toString().toUpperCase());
                    }
                }
            }
        } else if ("ON_UPDATE_CHANGE".equals(cmd.operation)) {
            JSONObject onUpdate = tableSchema.optJSONObject("on_update");
            if (onUpdate == null) {
                onUpdate = new JSONObject();
                tableSchema.put("on_update", onUpdate);
            }
            JSONObject extras = tableSchema.optJSONObject("extras");
            if (extras == null) {
                extras = new JSONObject();
                tableSchema.put("extras", extras);
            }
            JSONObject defaults = tableSchema.optJSONObject("defaults");
            String defVal = null;
            if (defaults != null && defaults.has(cmd.columnName)) {
                Object rawDef = defaults.get(cmd.columnName);
                if (rawDef != JSONObject.NULL && rawDef != null) {
                    defVal = rawDef.toString();
                }
            }

            if (cmd.dropOnUpdate) {
                onUpdate.put(cmd.columnName, JSONObject.NULL);
                extras.put(cmd.columnName, "");
            } else {
                onUpdate.put(cmd.columnName, cmd.onUpdateValue == null ? JSONObject.NULL : cmd.onUpdateValue);
                if (cmd.onUpdateValue != null && SqlDefaults.isCurrentTimestampFunction(cmd.onUpdateValue)) {
                    if (SqlDefaults.isCurrentTimestampFunction(defVal)) {
                        extras.put(cmd.columnName, "DEFAULT_GENERATED on update " + cmd.onUpdateValue.toUpperCase());
                    } else {
                        extras.put(cmd.columnName, "on update " + cmd.onUpdateValue.toUpperCase());
                    }
                } else {
                    extras.put(cmd.columnName, "");
                }
            }
        } else if ("ADD_CHECK".equals(cmd.operation)) {
            JSONObject chkObj = new JSONObject(cmd.checkConstraint);
            String targetCol = chkObj.optString("column");
            if (targetCol != null && !targetCol.isEmpty()) {
                boolean colFound = false;
                for (int cIdx = 0; cIdx < colsArr.length(); cIdx++) {
                    if (colsArr.getString(cIdx).equalsIgnoreCase(targetCol)) {
                        targetCol = colsArr.getString(cIdx);
                        colFound = true;
                        break;
                    }
                }
                if (!colFound) {
                    throw new Exception("Error: Key column '" + targetCol + "' doesn't exist in table");
                }
                chkObj.put("column", targetCol);
            }

            JSONArray checks = tableSchema.optJSONArray("checks");
            if (checks == null) {
                checks = new JSONArray();
                tableSchema.put("checks", checks);
            }

            if (cmd.constraintName != null) {
                for (int i = 0; i < checks.length(); i++) {
                    JSONObject existingChk = checks.getJSONObject(i);
                    if (cmd.constraintName.equalsIgnoreCase(existingChk.optString("name"))) {
                        throw new Exception("Error: Duplicate constraint name '" + cmd.constraintName + "'");
                    }
                }
                JSONObject indexesObj = tableSchema.optJSONObject("indexes");
                if (indexesObj != null) {
                    Iterator<String> iks = indexesObj.keys();
                    while (iks.hasNext()) {
                        if (iks.next().equalsIgnoreCase(cmd.constraintName)) {
                            throw new Exception("Error: Duplicate constraint name '" + cmd.constraintName + "'");
                        }
                    }
                }
                chkObj.put("name", cmd.constraintName);
            }
            checks.put(chkObj);
        } else if ("DROP_CHECK".equals(cmd.operation) || "DROP_CONSTRAINT".equals(cmd.operation)) {
            boolean removed = false;
            JSONArray checks = tableSchema.optJSONArray("checks");
            if (checks != null) {
                for (int i = checks.length() - 1; i >= 0; i--) {
                    JSONObject chk = checks.getJSONObject(i);
                    if (chk.optString("name").equalsIgnoreCase(cmd.constraintName)) {
                        checks.remove(i);
                        removed = true;
                        break;
                    }
                }
                if (!removed) {
                    String name = cmd.constraintName.toLowerCase();
                    int lastUnderscore = name.lastIndexOf('_');
                    if (lastUnderscore != -1) {
                        try {
                            int index = Integer.parseInt(name.substring(lastUnderscore + 1)) - 1;
                            if (index >= 0 && index < checks.length()) {
                                checks.remove(index);
                                removed = true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (!removed && "DROP_CONSTRAINT".equals(cmd.operation)) {
                JSONObject fks = tableSchema.optJSONObject("foreign_keys");
                if (fks != null && fks.has(cmd.constraintName)) {
                    fks.remove(cmd.constraintName);
                    removed = true;
                }
            }

            if (!removed && "DROP_CONSTRAINT".equals(cmd.operation)) {
                JSONObject indexesObj = tableSchema.optJSONObject("indexes");
                if (indexesObj != null) {
                    String matchedKey = null;
                    Iterator<String> iks = indexesObj.keys();
                    while (iks.hasNext()) {
                        String k = iks.next();
                        if (k.equalsIgnoreCase(cmd.constraintName)) {
                            matchedKey = k;
                            break;
                        }
                    }
                    if (matchedKey != null) {
                        JSONObject idxMeta = indexesObj.getJSONObject(matchedKey);
                        JSONArray cols = idxMeta.optJSONArray("columns");
                        JSONArray uniques = tableSchema.optJSONArray("uniques");
                        if (cols != null && uniques != null) {
                            for (int i = uniques.length() - 1; i >= 0; i--) {
                                JSONArray group = uniques.getJSONArray(i);
                                if (group.length() == cols.length()) {
                                    boolean groupMatch = true;
                                    for (int j = 0; j < group.length(); j++) {
                                        if (!group.getString(j).equalsIgnoreCase(cols.getString(j))) {
                                            groupMatch = false;
                                            break;
                                        }
                                    }
                                    if (groupMatch) {
                                        JSONObject keys = tableSchema.optJSONObject("keys");
                                        if (keys != null) {
                                            for (int j = 0; j < group.length(); j++) {
                                                keys.put(group.getString(j), "");
                                            }
                                        }
                                        uniques.remove(i);
                                    }
                                }
                            }
                        }
                        indexesObj.remove(matchedKey);
                        removed = true;
                    }
                }
            }

            if (!removed) {
                throw new Exception("Error: Constraint '" + cmd.constraintName + "' does not exist");
            }
        } else if ("AUTO_INCREMENT".equals(cmd.operation)) {
            tableSchema.put("auto_increment", cmd.autoIncrementValue);
            TableData td = tableCache.get(tableName);
            if (td != null) {
                td.autoIncrementCounters.clear();
            }
        }

        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        if ("ADD_COLUMN".equals(cmd.operation) || "MODIFY_COLUMN".equals(cmd.operation) || 
            "CHANGE_COLUMN".equals(cmd.operation) || "RENAME_COLUMN".equals(cmd.operation) || 
            "DROP_COLUMN".equals(cmd.operation)) {
            
            JSONArray rowsArr = storageEngine.readTableRows(activeDatabaseName, tableName);
            JSONArray updatedRows = new JSONArray();
            for (int i = 0; i < rowsArr.length(); i++) {
                JSONObject rowObj = rowsArr.getJSONObject(i);
                
                if ("ADD_COLUMN".equals(cmd.operation)) {
                    Command.ColumnDef cd = cmd.columnDef;
                    rowObj.put(cd.name, cd.defaultValue == null ? JSONObject.NULL : cd.defaultValue);
                } else if ("CHANGE_COLUMN".equals(cmd.operation)) {
                    String oldCol = cmd.columnName;
                    Command.ColumnDef cd = cmd.columnDef;
                    Object val = rowObj.opt(oldCol);
                    rowObj.remove(oldCol);
                    rowObj.put(cd.name, val == null ? JSONObject.NULL : val);
                } else if ("RENAME_COLUMN".equals(cmd.operation)) {
                    String oldCol = cmd.columnName;
                    String newCol = cmd.newColumnName;
                    Object val = rowObj.opt(oldCol);
                    rowObj.remove(oldCol);
                    rowObj.put(newCol, val == null ? JSONObject.NULL : val);
                } else if ("DROP_COLUMN".equals(cmd.operation)) {
                    rowObj.remove(cmd.columnName);
                }
                
                updatedRows.put(rowObj);
            }
            storageEngine.writeTableRows(activeDatabaseName, tableName, updatedRows);
        }

        tableCache.remove(tableName);

        return QueryResult.createSuccess("Table altered successfully", 0, 0);
    }

    public QueryResult createIndex(String tableName, String indexName, List<String> columns, boolean unique) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("ALTER", activeDatabaseName, tableName);
        ensureActiveSchema();
        if (!activeSchemaJson.has(tableName)) {
            throw new Exception("Error: Table '" + tableName + "' does not exist");
        }
        
        JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
        if (unique) {
            JSONArray uniques = tableSchema.optJSONArray("uniques");
            if (uniques == null) {
                uniques = new JSONArray();
                tableSchema.put("uniques", uniques);
            }
            JSONArray group = new JSONArray();
            JSONObject keys = tableSchema.optJSONObject("keys");
            for (String col : columns) {
                group.put(col);
                if (keys != null && "".equals(keys.optString(col))) {
                    keys.put(col, "UNI");
                }
            }
            uniques.put(group);
        }
        
        JSONObject indexesObj = tableSchema.optJSONObject("indexes");
        if (indexesObj == null) {
            indexesObj = new JSONObject();
            tableSchema.put("indexes", indexesObj);
        }
        JSONObject indexMeta = new JSONObject();
        indexMeta.put("name", indexName);
        indexMeta.put("columns", new JSONArray(columns));
        indexMeta.put("unique", unique);
        indexMeta.put("type", "BTREE");
        indexesObj.put(indexName, indexMeta);

        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);
        tableCache.remove(tableName);
        
        return QueryResult.createSuccess("Index created successfully", 0, 0);
    }

    public QueryResult truncateTable(String tableName) throws Exception {
        tableName = resolveTableName(tableName);
        verifyPrivilege("DROP", activeDatabaseName, tableName);
        ensureActiveSchema();
        if (!activeSchemaJson.has(tableName)) {
            throw new Exception("Error: Table '" + tableName + "' does not exist");
        }

        storageEngine.writeTableRows(activeDatabaseName, tableName, new JSONArray());

        TableData td = tableCache.get(tableName);
        if (td != null) {
            td.rows.clear();
        }

        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult renameTable(String oldName, String newName) throws Exception {
        oldName = resolveTableName(oldName);
        String resolvedNewName = resolveTableName(newName);
        verifyPrivilege("ALTER", activeDatabaseName, oldName);
        verifyPrivilege("CREATE", activeDatabaseName, newName);
        ensureActiveSchema();
        if (!activeSchemaJson.has(oldName)) {
            throw new Exception("Error: Table '" + oldName + "' does not exist");
        }
        if (activeSchemaJson.has(resolvedNewName)) {
            throw new Exception("Error: Table '" + newName + "' already exists");
        }

        JSONObject tableSchema = activeSchemaJson.getJSONObject(oldName);
        activeSchemaJson.put(newName, tableSchema);
        activeSchemaJson.remove(oldName);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        File databasesDir = storageEngine.getDatabasesDir();
        File dbDir = new File(databasesDir, activeDatabaseName);
        File oldFile = new File(dbDir, oldName + ".pqsql");
        File newFile = new File(dbDir, newName + ".pqsql");
        if (oldFile.exists()) {
            if (!oldFile.renameTo(newFile)) {
                throw new Exception("Error: Failed to rename table file on disk");
            }
        }

        tableCache.remove(oldName);
        tableCache.remove(newName);

        return QueryResult.createSuccess("Table renamed successfully", 0, 0);
    }

    public QueryResult revokePrivileges(List<String> privileges, String dbPattern, String username, String host) throws Exception {
        return privilegeManager.revokePrivileges(privileges, dbPattern, username, host);
    }

    public QueryResult startTransaction() throws Exception {
        return transactionManager.startTransaction();
    }

    public QueryResult commitTransaction() throws Exception {
        return transactionManager.commitTransaction();
    }

    public QueryResult rollbackTransaction() throws Exception {
        return transactionManager.rollbackTransaction();
    }

    public QueryResult createSavepoint(String name) throws Exception {
        return transactionManager.createSavepoint(name);
    }

    public QueryResult rollbackToSavepoint(String name) throws Exception {
        return transactionManager.rollbackToSavepoint(name);
    }

    public synchronized List<String> getTablesList() {
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) {
                return Collections.emptyList();
            }
            List<String> list = new ArrayList<>();
            Iterator<String> keys = activeSchemaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.startsWith("__")) {
                    list.add(key);
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getColumnsList(String tableName) {
        if (tableName == null) {
            return Collections.emptyList();
        }
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null || !activeSchemaJson.has(tableName)) {
                return Collections.emptyList();
            }
            JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
            JSONArray colsArr = tableSchema.optJSONArray("columns");
            if (colsArr == null) {
                return Collections.emptyList();
            }
            List<String> list = new ArrayList<>();
            for (int i = 0; i < colsArr.length(); i++) {
                list.add(colsArr.getString(i));
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getDatabasesList() {
        try {
            return storageEngine.listDatabases();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getProceduresList() {
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return Collections.emptyList();
            JSONObject procs = activeSchemaJson.optJSONObject("__procedures__");
            if (procs == null) return Collections.emptyList();
            List<String> list = new ArrayList<>();
            Iterator<String> keys = procs.keys();
            while (keys.hasNext()) {
                list.add(keys.next());
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getTriggersList() {
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return Collections.emptyList();
            JSONObject triggers = activeSchemaJson.optJSONObject("__triggers__");
            if (triggers == null) return Collections.emptyList();
            List<String> list = new ArrayList<>();
            Iterator<String> keys = triggers.keys();
            while (keys.hasNext()) {
                list.add(keys.next());
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getEventsList() {
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return Collections.emptyList();
            JSONObject events = activeSchemaJson.optJSONObject("__events__");
            if (events == null) return Collections.emptyList();
            List<String> list = new ArrayList<>();
            Iterator<String> keys = events.keys();
            while (keys.hasNext()) {
                list.add(keys.next());
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getCustomFunctionsList() {
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return Collections.emptyList();
            JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
            if (fns == null) return Collections.emptyList();
            List<String> list = new ArrayList<>();
            Iterator<String> keys = fns.keys();
            while (keys.hasNext()) {
                list.add(keys.next());
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized List<String> getConstraintsList() {
        List<String> list = new ArrayList<>();
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return list;
            Iterator<String> tbls = activeSchemaJson.keys();
            while (tbls.hasNext()) {
                String tableName = tbls.next();
                if (tableName.startsWith("__")) continue;
                JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
                
                JSONArray pk = tableSchema.optJSONArray("primary_key");
                if (pk != null && pk.length() > 0) {
                    list.add("PRIMARY");
                }
                
                JSONObject fkObj = tableSchema.optJSONObject("foreign_keys");
                if (fkObj != null) {
                    Iterator<String> fks = fkObj.keys();
                    while (fks.hasNext()) {
                        list.add("fk_" + fks.next());
                    }
                }
                
                JSONArray checks = tableSchema.optJSONArray("checks");
                if (checks != null) {
                    for (int i = 0; i < checks.length(); i++) {
                        list.add(tableName + "_chk_" + (i + 1));
                    }
                }
            }
        } catch (Exception e) {
            // silent
        }
        return list;
    }

    public synchronized List<String> getIndexesList() {
        List<String> list = new ArrayList<>();
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return list;
            Iterator<String> tbls = activeSchemaJson.keys();
            while (tbls.hasNext()) {
                String tableName = tbls.next();
                if (tableName.startsWith("__")) continue;
                JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
                JSONArray pk = tableSchema.optJSONArray("primary_key");
                if (pk != null && pk.length() > 0) {
                    if (!list.contains("PRIMARY")) list.add("PRIMARY");
                }
                JSONArray uniques = tableSchema.optJSONArray("uniques");
                if (uniques != null) {
                    for (int i = 0; i < uniques.length(); i++) {
                        JSONArray grp = uniques.getJSONArray(i);
                        if (grp.length() > 0) {
                            String uqName = "uq_" + grp.getString(0);
                            if (!list.contains(uqName)) list.add(uqName);
                        }
                    }
                }
                JSONObject indexesObj = tableSchema.optJSONObject("indexes");
                if (indexesObj != null) {
                    Iterator<String> idxNames = indexesObj.keys();
                    while (idxNames.hasNext()) {
                        String idxName = idxNames.next();
                        if (!list.contains(idxName)) {
                            list.add(idxName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // silent
        }
        return list;
    }

    public synchronized boolean hasCustomFunction(String name) {
        try {
            ensureActiveSchema();
            if (activeSchemaJson == null) return false;
            JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
            if (fns == null) return false;
            // Case-insensitive lookup
            Iterator<String> keys = fns.keys();
            while (keys.hasNext()) {
                if (keys.next().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // silent
        }
        return false;
    }

    public synchronized Object executeCustomFunction(String name, List<Object> argVals) throws Exception {
        ensureActiveSchema();
        JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
        if (fns == null) {
            throw new Exception("Function '" + name + "' does not exist");
        }
        
        // Find case-insensitive exact name
        String exactName = null;
        Iterator<String> keys = fns.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (k.equalsIgnoreCase(name)) {
                exactName = k;
                break;
            }
        }
        if (exactName == null) {
            throw new Exception("Function '" + name + "' does not exist");
        }

        JSONObject funcObj = fns.getJSONObject(exactName);

        // De-serialize parameters
        List<String> paramNames = new ArrayList<>();
        List<String> paramTypes = new ArrayList<>();
        JSONArray paramsArr = funcObj.getJSONArray("parameters");
        for (int i = 0; i < paramsArr.length(); i++) {
            JSONObject paramObj = paramsArr.getJSONObject(i);
            paramNames.add(paramObj.getString("name"));
            paramTypes.add(paramObj.getString("type"));
        }

        if (argVals.size() != paramNames.size()) {
            throw new Exception("Incorrect parameter count for function '" + exactName + "'; expected " + paramNames.size() + ", got " + argVals.size());
        }

        // De-serialize body tokens
        List<SqlToken> bodyTokens = new ArrayList<>();
        JSONArray bodyArr = funcObj.getJSONArray("body");
        for (int i = 0; i < bodyArr.length(); i++) {
            JSONObject tokObj = bodyArr.getJSONObject(i);
            SqlToken.Type type = SqlToken.Type.valueOf(tokObj.getString("type"));
            String val = tokObj.getString("value");
            int pos = tokObj.optInt("position", 0);
            bodyTokens.add(new SqlToken(type, val, pos));
        }

        // Parse statement blocks
        FunctionStatement.UdfBodyParser parser = new FunctionStatement.UdfBodyParser(bodyTokens);
        List<FunctionStatement> statements = parser.parse();

        // Scope map containing variables: arguments mapped to parameter names
        Map<String, Object> variables = new HashMap<>();
        for (int i = 0; i < paramNames.size(); i++) {
            variables.put(paramNames.get(i), argVals.get(i));
        }

        // Execute statements
        for (FunctionStatement stmt : statements) {
            Object res = stmt.execute(variables, this);
            if (res instanceof FunctionStatement.ReturnWrapper) {
                return ((FunctionStatement.ReturnWrapper) res).value;
            }
        }

        return null;
    }

    public synchronized QueryResult createFunction(String name, List<String> paramNames, List<String> paramTypes,
                                                   String returnType, List<SqlToken> bodyTokens) throws Exception {
        return createFunction(name, paramNames, paramTypes, returnType, bodyTokens, null);
    }

    public synchronized QueryResult createFunction(String name, List<String> paramNames, List<String> paramTypes,
                                                   String returnType, List<SqlToken> bodyTokens, String definition) throws Exception {
        verifyPrivilege("CREATE", activeDatabaseName, "*");
        ensureActiveSchema();

        // Validate table and column references in UDF body
        validateProcedureOrFunction(bodyTokens, new java.util.HashSet<>(paramNames));

        JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
        if (fns == null) {
            fns = new JSONObject();
            activeSchemaJson.put("__functions__", fns);
        }

        JSONObject funcObj = new JSONObject();
        funcObj.put("name", name);
        funcObj.put("returnType", returnType);

        // Serialize parameters
        JSONArray paramsArr = new JSONArray();
        for (int i = 0; i < paramNames.size(); i++) {
            JSONObject pObj = new JSONObject();
            pObj.put("name", paramNames.get(i));
            pObj.put("type", paramTypes.get(i));
            paramsArr.put(pObj);
        }
        funcObj.put("parameters", paramsArr);

        // Serialize body tokens
        JSONArray bodyArr = new JSONArray();
        for (SqlToken tok : bodyTokens) {
            JSONObject tokObj = new JSONObject();
            tokObj.put("type", tok.type.name());
            tokObj.put("value", tok.value);
            tokObj.put("position", tok.position);
            bodyArr.put(tokObj);
        }
        funcObj.put("body", bodyArr);
        if (definition != null) {
            funcObj.put("definition", definition);
        }

        fns.put(name, funcObj);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        return QueryResult.createSuccess("Function '" + name + "' created successfully", 1, 0);
    }

    public synchronized QueryResult dropFunction(String name, boolean ifExists) throws Exception {
        verifyPrivilege("DROP", activeDatabaseName, "*");
        ensureActiveSchema();

        JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
        if (fns == null || !fns.has(name)) {
            // Check case-insensitive lookup
            String exactName = null;
            if (fns != null) {
                Iterator<String> keys = fns.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (k.equalsIgnoreCase(name)) {
                        exactName = k;
                        break;
                    }
                }
            }
            if (exactName == null) {
                if (ifExists) {
                    return QueryResult.createSuccess("Function does not exist (ignored)", 0, 0);
                }
                throw new Exception("FUNCTION " + name + " does not exist");
            }
            name = exactName;
        }

        fns.remove(name);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        return QueryResult.createSuccess("Function dropped successfully", 1, 0);
    }

    public synchronized QueryResult showFunctionStatus(Clause.Where where) throws Exception {
        verifyPrivilege("SELECT", activeDatabaseName, "*");
        ensureActiveSchema();

        List<String> columns = new ArrayList<>();
        columns.add("Db");
        columns.add("Name");
        columns.add("Type");
        columns.add("Definer");
        columns.add("Modified");
        columns.add("Created");
        columns.add("Security_type");
        columns.add("Comment");
        columns.add("character_set_client");
        columns.add("collation_connection");
        columns.add("Database Collation");

        List<String> types = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            types.add("TEXT");
        }

        List<Map<String, Object>> rows = new ArrayList<>();

        JSONObject fns = activeSchemaJson.optJSONObject("__functions__");
        if (fns != null) {
            Iterator<String> keys = fns.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                Map<String, Object> row = new HashMap<>();
                row.put("Db", activeDatabaseName);
                row.put("db", activeDatabaseName);
                row.put("DB", activeDatabaseName);
                
                row.put("Name", name);
                row.put("name", name);
                row.put("NAME", name);
                
                row.put("Type", "FUNCTION");
                row.put("type", "FUNCTION");
                row.put("TYPE", "FUNCTION");
                
                String definer = (currentUser != null ? currentUser : SecurityHelper.getDefaultUser()) + "@" + (currentHost != null ? currentHost : SecurityHelper.getDefaultHost());
                row.put("Definer", definer);
                row.put("definer", definer);
                row.put("DEFINER", definer);
                
                String timeStr = "2026-05-30 00:00:00";
                row.put("Modified", timeStr);
                row.put("Created", timeStr);
                row.put("Security_type", "DEFINER");
                row.put("Comment", "");
                row.put("character_set_client", "utf8mb4");
                row.put("collation_connection", "utf8mb4_0900_ai_ci");
                row.put("Database Collation", "utf8mb4_0900_ai_ci");

                if (where == null || where.evaluate(row, null, this)) {
                    rows.add(row);
                }
            }
        }

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public synchronized QueryResult showCharacterSets() throws Exception {
        return SqlCollation.showCharacterSets();
    }

    public synchronized QueryResult showCollations() throws Exception {
        return SqlCollation.showCollations();
    }

    public synchronized QueryResult showCreateDatabase(String dbName) throws Exception {
        if (!storageEngine.databaseExists(dbName)) {
            throw new Exception("Error: Database '" + dbName + "' does not exist");
        }
        
        JSONObject schemaJson = storageEngine.readSchema(dbName);
        JSONObject dbMetadata = schemaJson.optJSONObject("__db_metadata__");
        
        String charset = "utf8mb4";
        String collation = "utf8mb4_0900_ai_ci";
        
        if (dbMetadata != null) {
            charset = dbMetadata.optString("default_character_set", charset);
            collation = dbMetadata.optString("default_collation", collation);
        }
        
        String sql = "CREATE DATABASE `" + dbName + "` /*!40100 DEFAULT CHARACTER SET " + charset + " COLLATE " + collation + " */";
        
        List<String> columns = new ArrayList<>();
        columns.add("Database");
        columns.add("Create Database");
        
        List<String> types = new ArrayList<>();
        types.add("TEXT");
        types.add("TEXT");
        
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("Database", dbName);
        row.put("Create Database", sql);
        rows.add(row);
        
        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public synchronized QueryResult showTableStatus(String databaseName, String likePattern, Clause.Where where) throws Exception {
        String targetDb = databaseName != null ? databaseName : activeDatabaseName;
        if (targetDb == null) {
            throw new Exception("No database selected");
        }
        verifyPrivilege("SELECT", targetDb, "*");

        JSONObject schemaJson;
        if (targetDb.equalsIgnoreCase(activeDatabaseName) && activeSchemaJson != null) {
            schemaJson = activeSchemaJson;
        } else {
            schemaJson = storageEngine.readSchema(targetDb);
        }

        List<String> columns = new ArrayList<>();
        columns.add("Name");
        columns.add("Engine");
        columns.add("Version");
        columns.add("Row_format");
        columns.add("Rows");
        columns.add("Avg_row_length");
        columns.add("Data_length");
        columns.add("Max_data_length");
        columns.add("Index_length");
        columns.add("Data_free");
        columns.add("Auto_increment");
        columns.add("Create_time");
        columns.add("Update_time");
        columns.add("Check_time");
        columns.add("Collation");
        columns.add("Checksum");
        columns.add("Create_options");
        columns.add("Comment");

        List<String> types = new ArrayList<>();
        types.add("VARCHAR");
        types.add("VARCHAR");
        types.add("BIGINT");
        types.add("VARCHAR");
        types.add("BIGINT");
        types.add("BIGINT");
        types.add("BIGINT");
        types.add("BIGINT");
        types.add("BIGINT");
        types.add("BIGINT");
        types.add("BIGINT");
        types.add("DATETIME");
        types.add("DATETIME");
        types.add("DATETIME");
        types.add("VARCHAR");
        types.add("VARCHAR");
        types.add("VARCHAR");
        types.add("VARCHAR");

        List<Map<String, Object>> resultRows = new ArrayList<>();
        if (schemaJson != null) {
            Iterator<String> keys = schemaJson.keys();
            List<String> tableNames = new ArrayList<>();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!k.startsWith("__")) {
                    tableNames.add(k);
                }
            }
            Collections.sort(tableNames);

            for (String tblName : tableNames) {
                JSONObject tblSchema = schemaJson.optJSONObject(tblName);
                if (tblSchema == null) continue;
                boolean isView = tblSchema.optBoolean("is_view", false);
                if (isView) continue;

                if (likePattern != null && !likePattern.isEmpty()) {
                    String regex = "^" + java.util.regex.Pattern.quote(likePattern)
                            .replace("%", ".*")
                            .replace("_", ".") + "$";
                    if (!tblName.matches("(?i)" + regex)) {
                        continue;
                    }
                }

                int rowCount = 0;
                long dataLength = 0;
                try {
                    JSONArray rowsArr = storageEngine.readTableRows(targetDb, tblName);
                    if (rowsArr != null) {
                        rowCount = rowsArr.length();
                        dataLength = rowsArr.toString().getBytes("UTF-8").length;
                    }
                } catch (Exception ignored) {}

                Object autoIncVal = JSONObject.NULL;
                if (tblSchema.has("auto_increment")) {
                    autoIncVal = tblSchema.optLong("auto_increment", 1L);
                }

                Map<String, Object> rowMap = new HashMap<>();
                rowMap.put("Name", tblName);
                rowMap.put("name", tblName);
                rowMap.put("NAME", tblName);
                rowMap.put("Engine", "InnoDB");
                rowMap.put("engine", "InnoDB");
                rowMap.put("ENGINE", "InnoDB");
                rowMap.put("Version", 10L);
                rowMap.put("Row_format", "Dynamic");
                rowMap.put("Rows", (long) rowCount);
                rowMap.put("rows", (long) rowCount);
                rowMap.put("Avg_row_length", rowCount > 0 ? dataLength / rowCount : 0L);
                rowMap.put("Data_length", dataLength);
                rowMap.put("Max_data_length", 0L);
                rowMap.put("Index_length", 0L);
                rowMap.put("Data_free", 0L);
                rowMap.put("Auto_increment", autoIncVal);
                rowMap.put("auto_increment", autoIncVal);
                rowMap.put("Create_time", "2026-01-01 00:00:00");
                rowMap.put("Update_time", JSONObject.NULL);
                rowMap.put("Check_time", JSONObject.NULL);
                rowMap.put("Collation", "utf8mb4_0900_ai_ci");
                rowMap.put("Checksum", JSONObject.NULL);
                rowMap.put("Create_options", "");
                rowMap.put("Comment", "");

                if (where != null && !where.evaluate(rowMap, null, this)) {
                    continue;
                }

                resultRows.add(rowMap);
            }
        }

        return QueryResult.createSelectSuccess(columns, types, resultRows, 0);
    }

    public synchronized QueryResult showCreateTable(String tableName) throws Exception {
        ensureActiveSchema();
        tableName = resolveTableName(tableName);
        if (!activeSchemaJson.has(tableName)) {
            throw new Exception("Error: Table '" + tableName + "' does not exist");
        }
        
        JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
        JSONArray colsArr = tableSchema.getJSONArray("columns");
        JSONArray typesArr = tableSchema.getJSONArray("types");
        JSONObject defaults = tableSchema.optJSONObject("defaults");
        JSONObject onUpdates = tableSchema.optJSONObject("on_update");
        JSONObject nullables = tableSchema.optJSONObject("nullables");
        JSONObject extras = tableSchema.optJSONObject("extras");
        
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE `").append(tableName).append("` (\n");
        
        int n = colsArr.length();
        for (int i = 0; i < n; i++) {
            String colName = colsArr.getString(i);
            String colType = typesArr.getString(i);
            
            sb.append("  `").append(colName).append("` ").append(colType);
            
            boolean nullable = nullables == null || nullables.optBoolean(colName, true);
            if (!nullable) {
                sb.append(" NOT NULL");
            }
            
            if (defaults != null && defaults.has(colName) && !defaults.isNull(colName)) {
                Object defVal = defaults.get(colName);
                if (defVal instanceof String) {
                    String defStr = (String) defVal;
                    if (SqlDefaults.isCurrentTimestampFunction(defStr) || "NULL".equalsIgnoreCase(defStr)) {
                        sb.append(" DEFAULT ").append(defStr);
                    } else {
                        sb.append(" DEFAULT '").append(defStr.replace("'", "''")).append("'");
                    }
                } else {
                    sb.append(" DEFAULT ").append(defVal.toString());
                }
            }
            
            if (onUpdates != null && onUpdates.has(colName) && !onUpdates.isNull(colName)) {
                sb.append(" ON UPDATE ").append(onUpdates.getString(colName));
            }
            
            if (extras != null && extras.has(colName) && !extras.isNull(colName)) {
                String extraStr = extras.getString(colName);
                if (!extraStr.isEmpty()) {
                    sb.append(" ").append(extraStr.toUpperCase());
                }
            }
            
            if (i < n - 1 || 
                (tableSchema.optJSONArray("primary_key") != null && tableSchema.getJSONArray("primary_key").length() > 0) ||
                (tableSchema.optJSONArray("uniques") != null && tableSchema.getJSONArray("uniques").length() > 0) ||
                (tableSchema.optJSONObject("foreign_keys") != null && tableSchema.getJSONObject("foreign_keys").length() > 0) ||
                (tableSchema.optJSONArray("checks") != null && tableSchema.getJSONArray("checks").length() > 0)) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }
        
        List<String> constraintLines = new ArrayList<>();
        
        JSONArray pkArr = tableSchema.optJSONArray("primary_key");
        if (pkArr != null && pkArr.length() > 0) {
            StringBuilder pkSb = new StringBuilder();
            pkSb.append("  PRIMARY KEY (");
            for (int i = 0; i < pkArr.length(); i++) {
                if (i > 0) pkSb.append(",");
                pkSb.append("`").append(pkArr.getString(i)).append("`");
            }
            pkSb.append(")");
            constraintLines.add(pkSb.toString());
        }
        
        JSONObject indexesObj = tableSchema.optJSONObject("indexes");
        if (indexesObj != null && indexesObj.length() > 0) {
            Iterator<String> iks = indexesObj.keys();
            while (iks.hasNext()) {
                String idxName = iks.next();
                if ("PRIMARY".equalsIgnoreCase(idxName)) continue;
                JSONObject idxMeta = indexesObj.optJSONObject(idxName);
                if (idxMeta == null) continue;
                JSONArray cols = idxMeta.optJSONArray("columns");
                if (cols == null) continue;
                boolean isUnique = idxMeta.optBoolean("unique", false);
                StringBuilder idxSb = new StringBuilder();
                if (isUnique) {
                    idxSb.append("  UNIQUE KEY `").append(idxName).append("` (");
                } else {
                    idxSb.append("  KEY `").append(idxName).append("` (");
                }
                for (int j = 0; j < cols.length(); j++) {
                    if (j > 0) idxSb.append(",");
                    idxSb.append("`").append(cols.getString(j)).append("`");
                }
                idxSb.append(")");
                constraintLines.add(idxSb.toString());
            }
        } else {
            JSONArray uniquesArr = tableSchema.optJSONArray("uniques");
            if (uniquesArr != null && uniquesArr.length() > 0) {
                for (int i = 0; i < uniquesArr.length(); i++) {
                    JSONArray group = uniquesArr.getJSONArray(i);
                    StringBuilder uniqSb = new StringBuilder();
                    uniqSb.append("  UNIQUE KEY `");
                    StringBuilder nameSb = new StringBuilder();
                    for (int j = 0; j < group.length(); j++) {
                        if (j > 0) nameSb.append("_");
                        nameSb.append(group.getString(j));
                    }
                    uniqSb.append(nameSb.toString()).append("` (");
                    for (int j = 0; j < group.length(); j++) {
                        if (j > 0) uniqSb.append(",");
                        uniqSb.append("`").append(group.getString(j)).append("`");
                    }
                    uniqSb.append(")");
                    constraintLines.add(uniqSb.toString());
                }
            }
        }
        
        JSONObject fks = tableSchema.optJSONObject("foreign_keys");
        if (fks != null && fks.length() > 0) {
            Iterator<String> keys = fks.keys();
            while (keys.hasNext()) {
                String childCol = keys.next();
                String refVal = fks.getString(childCol);
                int dotIdx = refVal.indexOf('.');
                if (dotIdx != -1) {
                    String parentTbl = refVal.substring(0, dotIdx);
                    String parentCol = refVal.substring(dotIdx + 1);
                    constraintLines.add("  CONSTRAINT `fk_" + tableName + "_" + childCol + "` FOREIGN KEY (`" + childCol + "`) REFERENCES `" + parentTbl + "` (`" + parentCol + "`)");
                }
            }
        }
        
        JSONArray checks = tableSchema.optJSONArray("checks");
        if (checks != null && checks.length() > 0) {
            for (int i = 0; i < checks.length(); i++) {
                JSONObject chkObj = checks.getJSONObject(i);
                String chkCol = chkObj.getString("column");
                String chkOp = chkObj.getString("operator");
                
                StringBuilder chkSb = new StringBuilder();
                chkSb.append("  CONSTRAINT `chk_").append(tableName).append("_").append(i+1).append("` CHECK ((`").append(chkCol).append("` ").append(chkOp).append(" ");
                if ("BETWEEN".equalsIgnoreCase(chkOp)) {
                    JSONArray vals = chkObj.getJSONArray("values");
                    chkSb.append(formatCheckVal(vals.get(0))).append(" AND ").append(formatCheckVal(vals.get(1)));
                } else if ("IN".equalsIgnoreCase(chkOp)) {
                    JSONArray vals = chkObj.getJSONArray("values");
                    chkSb.append("(");
                    for (int j = 0; j < vals.length(); j++) {
                        if (j > 0) chkSb.append(",");
                        chkSb.append(formatCheckVal(vals.get(j)));
                    }
                    chkSb.append(")");
                } else {
                    chkSb.append(formatCheckVal(chkObj.get("value")));
                }
                chkSb.append("))");
                constraintLines.add(chkSb.toString());
            }
        }
        
        for (int i = 0; i < constraintLines.size(); i++) {
            sb.append(constraintLines.get(i));
            if (i < constraintLines.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }
        
        sb.append(")");
        
        String tblCharset = tableSchema.optString("charset", "utf8mb4");
        String tblCollation = tableSchema.optString("collation", "utf8mb4_0900_ai_ci");
        sb.append(" ENGINE=InnoDB DEFAULT CHARSET=").append(tblCharset).append(" COLLATE=").append(tblCollation);
        
        List<String> columns = new ArrayList<>();
        columns.add("Table");
        columns.add("Create Table");
        
        List<String> types = new ArrayList<>();
        types.add("TEXT");
        types.add("TEXT");
        
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("Table", tableName);
        row.put("Create Table", sb.toString());
        rows.add(row);
        
        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    private String formatCheckVal(Object val) {
        if (val instanceof String) {
            return "'" + ((String) val).replace("'", "''") + "'";
        }
        return val.toString();
    }

    public synchronized QueryResult alterDatabase(String dbName, String charset, String collation) throws Exception {
        verifyPrivilege("ALTER", activeDatabaseName, "*");
        
        String targetDb = dbName;
        if (targetDb == null) {
            checkActiveDatabase();
            targetDb = activeDatabaseName;
        }
        
        if (!storageEngine.databaseExists(targetDb)) {
            throw new Exception("Error: Database '" + targetDb + "' does not exist");
        }
        
        JSONObject schemaJson = storageEngine.readSchema(targetDb);
        JSONObject dbMetadata = schemaJson.optJSONObject("__db_metadata__");
        if (dbMetadata == null) {
            dbMetadata = new JSONObject();
            schemaJson.put("__db_metadata__", dbMetadata);
        }
        
        String finalCharset = charset;
        String finalCollation = collation;
        
        if (finalCharset != null) {
            if (!SqlCollation.isValidCharset(finalCharset)) {
                throw new Exception("Error: Unknown character set: '" + finalCharset + "'");
            }
        }
        if (finalCollation != null) {
            if (!SqlCollation.isValidCollation(finalCollation)) {
                throw new Exception("Error: Unknown collation: '" + finalCollation + "'");
            }
        }
        
        if (finalCharset != null && finalCollation == null) {
            finalCollation = SqlCollation.getDefaultCollationForCharset(finalCharset);
        } else if (finalCharset == null && finalCollation != null) {
            finalCharset = SqlCollation.getCharsetForCollation(finalCollation);
        } else if (finalCharset != null && finalCollation != null) {
            String expectedCharset = SqlCollation.getCharsetForCollation(finalCollation);
            if (!expectedCharset.equalsIgnoreCase(finalCharset)) {
                throw new Exception("Error: Collation '" + finalCollation + "' is not valid for character set '" + finalCharset + "'");
            }
        }
        
        if (finalCharset != null) {
            dbMetadata.put("default_character_set", finalCharset.toLowerCase());
        }
        if (finalCollation != null) {
            dbMetadata.put("default_collation", finalCollation.toLowerCase());
        }
        
        storageEngine.writeSchema(targetDb, schemaJson);
        
        if (targetDb.equalsIgnoreCase(activeDatabaseName)) {
            activeSchemaJson = schemaJson;
        }
        
        return QueryResult.createSuccess("Database altered successfully", 1, 0);
    }

    public synchronized QueryResult alterTableConvert(String tableName, String charset, String collation) throws Exception {
        verifyPrivilege("ALTER", activeDatabaseName, tableName);
        ensureActiveSchema();
        if (!activeSchemaJson.has(tableName)) {
            throw new Exception("Error: Table '" + tableName + "' does not exist");
        }
        
        JSONObject tableSchema = activeSchemaJson.getJSONObject(tableName);
        JSONArray typesArr = tableSchema.getJSONArray("types");
        
        String finalCharset = charset;
        String finalCollation = collation;
        
        if (finalCharset != null) {
            if (!SqlCollation.isValidCharset(finalCharset)) {
                throw new Exception("Error: Unknown character set: '" + finalCharset + "'");
            }
        }
        if (finalCollation != null) {
            if (!SqlCollation.isValidCollation(finalCollation)) {
                throw new Exception("Error: Unknown collation: '" + finalCollation + "'");
            }
        }
        
        if (finalCharset != null && finalCollation == null) {
            finalCollation = SqlCollation.getDefaultCollationForCharset(finalCharset);
        } else if (finalCharset == null && finalCollation != null) {
            finalCharset = SqlCollation.getCharsetForCollation(finalCollation);
        } else if (finalCharset != null && finalCollation != null) {
            String expectedCharset = SqlCollation.getCharsetForCollation(finalCollation);
            if (!expectedCharset.equalsIgnoreCase(finalCharset)) {
                throw new Exception("Error: Collation '" + finalCollation + "' is not valid for character set '" + finalCharset + "'");
            }
        }
        
        tableSchema.put("charset", finalCharset.toLowerCase());
        tableSchema.put("collation", finalCollation.toLowerCase());
        
        for (int i = 0; i < typesArr.length(); i++) {
            String colType = typesArr.getString(i);
            String typeUpper = colType.toUpperCase().trim();
            boolean isCharType = typeUpper.startsWith("VARCHAR") ||
                                 typeUpper.startsWith("CHAR") ||
                                 typeUpper.startsWith("TEXT") ||
                                 typeUpper.startsWith("TINYTEXT") ||
                                 typeUpper.startsWith("MEDIUMTEXT") ||
                                 typeUpper.startsWith("LONGTEXT") ||
                                 typeUpper.startsWith("ENUM") ||
                                 typeUpper.startsWith("SET");
            
            if (isCharType) {
                String baseType = colType;
                int idx = indexOfIgnoreCase(baseType, " CHARACTER SET");
                if (idx != -1) baseType = baseType.substring(0, idx);
                idx = indexOfIgnoreCase(baseType, " CHARSET");
                if (idx != -1) baseType = baseType.substring(0, idx);
                idx = indexOfIgnoreCase(baseType, " COLLATE");
                if (idx != -1) baseType = baseType.substring(0, idx);
                
                String newType = baseType.trim() + " CHARACTER SET " + finalCharset + " COLLATE " + finalCollation;
                typesArr.put(i, newType);
            }
        }
        
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);
        tableCache.remove(tableName);
        
        return QueryResult.createSuccess("Table altered successfully", 0, 0);
    }

    public String getColumnCollation(String tableName, String columnName) {
        if (activeSchemaJson == null || tableName == null || columnName == null) {
            return "utf8mb4_0900_ai_ci";
        }
        try {
            JSONObject tableSchema = activeSchemaJson.optJSONObject(tableName);
            if (tableSchema == null) return "utf8mb4_0900_ai_ci";
            
            String baseCollation = null;
            JSONArray colsArr = tableSchema.optJSONArray("columns");
            JSONArray typesArr = tableSchema.optJSONArray("types");
            if (colsArr != null && typesArr != null) {
                for (int i = 0; i < colsArr.length(); i++) {
                    String col = colsArr.getString(i);
                    if (col.equalsIgnoreCase(columnName)) {
                        String type = typesArr.getString(i);
                        int collIdx = indexOfIgnoreCase(type, "COLLATE");
                        if (collIdx != -1) {
                            String remaining = type.substring(collIdx + 7).trim();
                            String[] parts = remaining.split("\\s+");
                            if (parts.length > 0) {
                                baseCollation = parts[0].trim();
                            }
                        }
                        break;
                    }
                }
            }
            if (baseCollation == null) {
                baseCollation = tableSchema.optString("collation", "utf8mb4_0900_ai_ci");
            }
            
            // Check for BINARY attribute
            JSONObject attrsObj = tableSchema.optJSONObject("attributes");
            if (attrsObj != null) {
                JSONObject colAttrs = attrsObj.optJSONObject(columnName);
                if (colAttrs != null) {
                    SqlAttributes attrs = SqlAttributes.fromJsonObject(colAttrs);
                    if (attrs.binaryAttr) {
                        String charset = SqlCollation.getCharsetForCollation(baseCollation);
                        if ("binary".equalsIgnoreCase(charset)) {
                            return "binary";
                        } else {
                            return charset.toLowerCase() + "_bin";
                        }
                    }
                }
            }
            
            return baseCollation;
        } catch (Exception e) {
            return "utf8mb4_0900_ai_ci";
        }
    }

    public String resolveCollation(String columnExpr) {
        return resolveCollation(null, columnExpr);
    }

    public String resolveCollation(String defaultTable, String columnExpr) {
        if (columnExpr == null) return "utf8mb4_0900_ai_ci";
        String colName = columnExpr;
        String tblName = defaultTable;
        
        int dotIdx = columnExpr.indexOf('.');
        if (dotIdx != -1) {
            tblName = columnExpr.substring(0, dotIdx);
            colName = columnExpr.substring(dotIdx + 1);
        }
        
        if (tblName != null) {
            try {
                if (activeSchemaJson != null && activeSchemaJson.has(tblName)) {
                    JSONObject tableSchema = activeSchemaJson.getJSONObject(tblName);
                    JSONArray colsArr = tableSchema.optJSONArray("columns");
                    if (colsArr != null) {
                        for (int i = 0; i < colsArr.length(); i++) {
                            if (colsArr.getString(i).equalsIgnoreCase(colName)) {
                                return getColumnCollation(tblName, colName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // fallback
            }
        }
        
        if (defaultTable != null && activeSchemaJson != null && activeSchemaJson.has(defaultTable)) {
            try {
                JSONObject tableSchema = activeSchemaJson.getJSONObject(defaultTable);
                JSONArray colsArr = tableSchema.optJSONArray("columns");
                if (colsArr != null) {
                    for (int i = 0; i < colsArr.length(); i++) {
                        if (colsArr.getString(i).equalsIgnoreCase(colName)) {
                            return getColumnCollation(defaultTable, colName);
                        }
                    }
                }
            } catch (Exception e) {
                // fallback
            }
        }
        
        if (activeSchemaJson != null) {
            Iterator<String> tables = activeSchemaJson.keys();
            while (tables.hasNext()) {
                String tName = tables.next();
                if (tName.startsWith("__")) continue;
                if (tName.equalsIgnoreCase(defaultTable)) continue;
                try {
                    JSONObject tableSchema = activeSchemaJson.getJSONObject(tName);
                    JSONArray colsArr = tableSchema.optJSONArray("columns");
                    if (colsArr != null) {
                        for (int i = 0; i < colsArr.length(); i++) {
                            if (colsArr.getString(i).equalsIgnoreCase(colName)) {
                                return getColumnCollation(tName, colName);
                            }
                        }
                    }
                } catch (Exception e) {
                    // skip
                }
            }
        }
        
        if (activeSchemaJson != null) {
            JSONObject dbMetadata = activeSchemaJson.optJSONObject("__db_metadata__");
            if (dbMetadata != null) {
                return dbMetadata.optString("default_collation", "utf8mb4_0900_ai_ci");
            }
        }
        
        return "utf8mb4_0900_ai_ci";
    }

    public boolean isForeignKeyChecksEnabled() {
        return foreignKeyChecks;
    }

    public void setDeferWrite(boolean defer) {
        this.deferWrite = defer;
    }

    public boolean isDeferWrite() {
        return deferWrite;
    }

    public void setConstraintsEnabled(boolean enabled) {
        this.constraintsEnabled = enabled;
    }

    public boolean isConstraintsEnabled() {
        return constraintsEnabled;
    }

    public void saveDirtyTables() throws Exception {
        if (activeDatabaseName == null) return;
        for (Map.Entry<String, TableData> entry : tableCache.entrySet()) {
            TableData td = entry.getValue();
            if (td.isDirty) {
                storageEngine.writeTableRows(activeDatabaseName, entry.getKey(), td.toJSONArray());
                td.isDirty = false;
            }
        }
    }

    public QueryResult setVariable(String name, Object value) throws Exception {
        if (name == null) {
            throw new Exception("Variable name cannot be null");
        }
        String nameUpper = name.toUpperCase();
        if ("FOREIGN_KEY_CHECKS".equals(nameUpper)) {
            boolean enabled;
            if (value instanceof Boolean) {
                enabled = (Boolean) value;
            } else if (value instanceof Number) {
                enabled = ((Number) value).doubleValue() != 0.0;
            } else if (value instanceof String) {
                String valStr = ((String) value).toUpperCase().trim();
                enabled = "ON".equals(valStr) || "TRUE".equals(valStr) || "1".equals(valStr);
            } else {
                enabled = false;
            }
            this.foreignKeyChecks = enabled;
            systemVariables.put("foreign_key_checks", enabled ? "1" : "0");
            return QueryResult.createSuccess("Variable 'foreign_key_checks' set to " + (enabled ? "1" : "0"), 0, 0);
        } else if (name.startsWith("@")) {
            userVariables.put(name.toLowerCase(), value);
            return QueryResult.createSuccess("Variable '" + name.toLowerCase() + "' set to " + (value != null ? value.toString() : "null"), 0, 0);
        } else {
            String valStr = value != null ? value.toString() : "null";
            systemVariables.put(name.toLowerCase(), valStr);
            return QueryResult.createSuccess("Variable '" + name.toLowerCase() + "' set to " + valStr, 0, 0);
        }
    }

    public QueryResult showVariables(String likePattern, Clause.Where where) {
        List<String> cols = java.util.Arrays.asList("Variable_name", "Value");
        List<String> types = java.util.Arrays.asList("VARCHAR(64)", "VARCHAR(1024)");
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Map.Entry<String, Object> entry : systemVariables.entrySet()) {
            String varName = entry.getKey();
            String varVal = entry.getValue() != null ? entry.getValue().toString() : "";

            if (likePattern != null && !likePattern.trim().isEmpty()) {
                if (!matchesLike(varName, likePattern.trim())) {
                    continue;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Variable_name", varName);
            row.put("Value", varVal);

            if (where != null && !where.evaluate(row, "utf8mb4_general_ci", this)) {
                continue;
            }

            rows.add(row);
        }

        return QueryResult.createSelectSuccess(cols, types, rows, 0);
    }

    public QueryResult showStatus(String likePattern, Clause.Where where) {
        List<String> cols = java.util.Arrays.asList("Variable_name", "Value");
        List<String> types = java.util.Arrays.asList("VARCHAR(64)", "VARCHAR(1024)");
        List<Map<String, Object>> rows = new ArrayList<>();

        Map<String, String> statusMap = new LinkedHashMap<>();
        statusMap.put("Uptime", String.valueOf((System.currentTimeMillis() - startTimeMs) / 1000));
        statusMap.put("Threads_connected", "1");
        statusMap.put("Threads_running", "1");
        statusMap.put("Questions", String.valueOf(statementCount));
        statusMap.put("Slow_queries", "0");
        statusMap.put("Opens", "1");
        statusMap.put("Flush_commands", "1");
        statusMap.put("Open_tables", "1");
        statusMap.put("Queries_per_second_avg", "0.000");

        for (Map.Entry<String, String> entry : statusMap.entrySet()) {
            String name = entry.getKey();
            String val = entry.getValue();

            if (likePattern != null && !likePattern.trim().isEmpty()) {
                if (!matchesLike(name, likePattern.trim())) continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Variable_name", name);
            row.put("Value", val);

            if (where != null && !where.evaluate(row, "utf8mb4_general_ci", this)) continue;
            rows.add(row);
        }

        return QueryResult.createSelectSuccess(cols, types, rows, 0);
    }

    public QueryResult showWarnings() {
        List<String> cols = java.util.Arrays.asList("Level", "Code", "Message");
        List<String> types = java.util.Arrays.asList("VARCHAR(10)", "INT", "VARCHAR(1024)");
        List<Map<String, Object>> rows = new ArrayList<>();
        return QueryResult.createSelectSuccess(cols, types, rows, 0);
    }

    public QueryResult showErrors() {
        List<String> cols = java.util.Arrays.asList("Level", "Code", "Message");
        List<String> types = java.util.Arrays.asList("VARCHAR(10)", "INT", "VARCHAR(1024)");
        List<Map<String, Object>> rows = new ArrayList<>();
        return QueryResult.createSelectSuccess(cols, types, rows, 0);
    }

    public QueryResult showProcesslist() {
        List<String> cols = java.util.Arrays.asList("Id", "User", "Host", "db", "Command", "Time", "State", "Info");
        List<String> types = java.util.Arrays.asList("INT", "VARCHAR(64)", "VARCHAR(64)", "VARCHAR(64)", "VARCHAR(16)", "INT", "VARCHAR(64)", "TEXT");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("Id", 1L);
        r.put("User", currentUser != null ? currentUser : "root");
        r.put("Host", currentHost != null ? currentHost : "localhost");
        r.put("db", activeDatabaseName != null ? activeDatabaseName : "NULL");
        r.put("Command", "Query");
        r.put("Time", 0L);
        r.put("State", "executing");
        r.put("Info", "SHOW PROCESSLIST");
        rows.add(r);
        return QueryResult.createSelectSuccess(cols, types, rows, 0);
    }

    public QueryResult showGrants(String userHost) {
        String targetUser = currentUser != null ? currentUser : "root";
        String targetHost = currentHost != null ? currentHost : "localhost";
        if (userHost != null && userHost.contains("@")) {
            String[] parts = userHost.split("@");
            targetUser = parts[0].replace("'", "");
            targetHost = parts[1].replace("'", "");
        }
        String grantCol = "Grants for " + targetUser + "@" + targetHost;
        List<String> cols = Collections.singletonList(grantCol);
        List<String> types = Collections.singletonList("VARCHAR(1024)");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(grantCol, "GRANT ALL PRIVILEGES ON *.* TO '" + targetUser + "'@'" + targetHost + "' WITH GRANT OPTION");
        rows.add(r);
        return QueryResult.createSelectSuccess(cols, types, rows, 0);
    }

    private boolean matchesLike(String text, String pattern) {
        if (text == null || pattern == null) return false;
        String regex = pattern
            .replace("\\", "\\\\")
            .replace(".", "\\.")
            .replace("+", "\\+")
            .replace("*", "\\*")
            .replace("?", "\\?")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("|", "\\|")
            .replace("%", ".*")
            .replace("_", ".");
        return text.matches("(?i)" + regex);
    }

    public QueryResult help(String topic) throws Exception {
        return SqlHelpManager.getHelp(topic);
    }

    public QueryResult pragma(String pragmaName, String arg) throws Exception {
        if (pragmaName == null) {
            throw new Exception("PRAGMA name cannot be null");
        }
        String pName = pragmaName.toLowerCase();
        ensureActiveSchema();

        if ("table_info".equals(pName)) {
            if (arg == null || arg.trim().isEmpty()) {
                throw new Exception("PRAGMA table_info requires a table name");
            }
            String tableName = arg.trim().replace("'", "").replace("\"", "").replace("`", "");
            if (activeSchemaJson == null || !activeSchemaJson.has(tableName)) {
                throw new Exception("Table '" + tableName + "' does not exist");
            }

            JSONObject ts = activeSchemaJson.getJSONObject(tableName);
            JSONArray colArr = ts.getJSONArray("columns");
            JSONArray typArr = ts.getJSONArray("types");
            JSONObject nulls = ts.optJSONObject("nullables");
            JSONObject defs = ts.optJSONObject("defaults");
            String primaryKey = ts.optString("primary_key", "");

            List<String> cols = java.util.Arrays.asList("cid", "name", "type", "notnull", "dflt_value", "pk");
            List<String> types = java.util.Arrays.asList("INT", "TEXT", "TEXT", "INT", "TEXT", "INT");
            List<Map<String, Object>> rows = new ArrayList<>();

            for (int i = 0; i < colArr.length(); i++) {
                String colName = colArr.getString(i);
                String colType = typArr.getString(i);
                boolean isNull = nulls != null ? nulls.optBoolean(colName, true) : true;
                Object defVal = defs != null ? defs.opt(colName) : null;
                boolean isPri = colName.equalsIgnoreCase(primaryKey);

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("cid", i);
                r.put("name", colName);
                r.put("type", colType);
                r.put("notnull", isNull ? 0 : 1);
                r.put("dflt_value", defVal != null && defVal != JSONObject.NULL ? defVal.toString() : null);
                r.put("pk", isPri ? 1 : 0);
                rows.add(r);
            }
            return QueryResult.createSelectSuccess(cols, types, rows, 0);
        } else if ("database_list".equals(pName)) {
            List<String> cols = java.util.Arrays.asList("seq", "name", "file");
            List<String> types = java.util.Arrays.asList("INT", "TEXT", "TEXT");
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("seq", 0);
            r.put("name", activeDatabaseName != null ? activeDatabaseName : "main");
            r.put("file", "");
            rows.add(r);
            return QueryResult.createSelectSuccess(cols, types, rows, 0);
        } else if ("index_list".equals(pName)) {
            List<String> cols = java.util.Arrays.asList("seq", "name", "unique", "origin", "partial");
            List<String> types = java.util.Arrays.asList("INT", "TEXT", "INT", "TEXT", "INT");
            List<Map<String, Object>> rows = new ArrayList<>();

            if (arg != null && !arg.trim().isEmpty() && activeSchemaJson != null) {
                String tableName = arg.trim().replace("'", "").replace("\"", "").replace("`", "");
                if (activeSchemaJson.has(tableName)) {
                    JSONObject ts = activeSchemaJson.getJSONObject(tableName);
                    JSONObject indexesObj = ts.optJSONObject("indexes");
                    if (indexesObj != null) {
                        Iterator<String> keys = indexesObj.keys();
                        int seq = 0;
                        while (keys.hasNext()) {
                            String idxName = keys.next();
                            JSONObject idxMeta = indexesObj.getJSONObject(idxName);
                            boolean isUnique = idxMeta.optBoolean("unique", false);
                            Map<String, Object> r = new LinkedHashMap<>();
                            r.put("seq", seq++);
                            r.put("name", idxName);
                            r.put("unique", isUnique ? 1 : 0);
                            r.put("origin", "c");
                            r.put("partial", 0);
                            rows.add(r);
                        }
                    }
                }
            }
            return QueryResult.createSelectSuccess(cols, types, rows, 0);
        } else if ("foreign_key_list".equals(pName)) {
            List<String> cols = java.util.Arrays.asList("id", "seq", "table", "from", "to", "on_update", "on_delete", "match");
            List<String> types = java.util.Arrays.asList("INT", "INT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT");
            List<Map<String, Object>> rows = new ArrayList<>();

            if (arg != null && !arg.trim().isEmpty() && activeSchemaJson != null) {
                String tableName = arg.trim().replace("'", "").replace("\"", "").replace("`", "");
                if (activeSchemaJson.has(tableName)) {
                    JSONObject ts = activeSchemaJson.getJSONObject(tableName);
                    JSONArray fks = ts.optJSONArray("foreign_keys");
                    if (fks != null) {
                        for (int i = 0; i < fks.length(); i++) {
                            JSONObject fkObj = fks.getJSONObject(i);
                            Map<String, Object> r = new LinkedHashMap<>();
                            r.put("id", i);
                            r.put("seq", 0);
                            r.put("table", fkObj.optString("ref_table", fkObj.optString("referenced_table", "")));
                            r.put("from", fkObj.optString("column", ""));
                            r.put("to", fkObj.optString("ref_column", fkObj.optString("referenced_column", "")));
                            r.put("on_update", fkObj.optString("on_update", "NO ACTION"));
                            r.put("on_delete", fkObj.optString("on_delete", "NO ACTION"));
                            r.put("match", "NONE");
                            rows.add(r);
                        }
                    }
                }
            }
            return QueryResult.createSelectSuccess(cols, types, rows, 0);
        } else if ("foreign_keys".equals(pName)) {
            if (arg != null && !arg.trim().isEmpty()) {
                String valUpper = arg.toUpperCase().trim();
                this.foreignKeyChecks = "ON".equals(valUpper) || "1".equals(valUpper) || "TRUE".equals(valUpper);
                return QueryResult.createSuccess("PRAGMA foreign_keys set to " + (foreignKeyChecks ? "ON" : "OFF"), 0, 0);
            } else {
                List<String> cols = Collections.singletonList("foreign_keys");
                List<String> types = Collections.singletonList("INT");
                List<Map<String, Object>> rows = new ArrayList<>();
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("foreign_keys", foreignKeyChecks ? 1 : 0);
                rows.add(r);
                return QueryResult.createSelectSuccess(cols, types, rows, 0);
            }
        } else if ("user_version".equals(pName)) {
            List<String> cols = Collections.singletonList("user_version");
            List<String> types = Collections.singletonList("INT");
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("user_version", 1);
            rows.add(r);
            return QueryResult.createSelectSuccess(cols, types, rows, 0);
        } else {
            return QueryResult.createSuccess("PRAGMA executed successfully", 0, 0);
        }
    }

    private int indexOfIgnoreCase(String src, String search) {
        String srcUpper = src.toUpperCase();
        String searchUpper = search.toUpperCase();
        return srcUpper.indexOf(searchUpper);
    }

    private JSONObject ensureActiveSchema() throws Exception {
        checkActiveDatabase();
        if (activeSchemaJson == null) {
            activeSchemaJson = storageEngine.readSchema(activeDatabaseName);
        }
        return activeSchemaJson;
    }

    private QueryResult createCatalogObject(String objName, String objDef, String catalogKey, String objType) throws Exception {
        verifyPrivilege("CREATE", activeDatabaseName, objName);
        ensureActiveSchema();

        JSONObject catalog = activeSchemaJson.optJSONObject(catalogKey);
        if (catalog == null) {
            catalog = new JSONObject();
            activeSchemaJson.put(catalogKey, catalog);
        }

        JSONObject obj = new JSONObject();
        obj.put("name", objName);
        obj.put("definition", objDef);
        obj.put("db", activeDatabaseName);

        catalog.put(objName, obj);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        return QueryResult.createSuccess(objType + " created successfully", 0, 0);
    }

    private QueryResult dropCatalogObject(String objName, String catalogKey, String objType, boolean ifExists) throws Exception {
        verifyPrivilege("DROP", activeDatabaseName, objName);
        ensureActiveSchema();

        JSONObject catalog = activeSchemaJson.optJSONObject(catalogKey);
        if (catalog == null || !catalog.has(objName)) {
            if (ifExists) {
                return QueryResult.createSuccess(objType + " does not exist (ignored)", 0, 0);
            }
            return QueryResult.createError("Error: " + objType + " '" + objName + "' does not exist");
        }

        catalog.remove(objName);
        storageEngine.writeSchema(activeDatabaseName, activeSchemaJson);

        return QueryResult.createSuccess(objType + " dropped successfully", 0, 0);
    }

    public QueryResult exportDatabase(String dbName, String filePath) throws Exception {
        verifyPrivilege("SELECT", dbName, "*");
        
        String format = "db";
        int dot = filePath.lastIndexOf('.');
        if (dot >= 0) {
            format = filePath.substring(dot + 1).toLowerCase();
        }
        
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (java.io.OutputStream os = new java.io.FileOutputStream(file)) {
            DatabaseExporter.exportDatabase(this, dbName, os, format);
        }
        
        return QueryResult.createSuccess("Database '" + dbName + "' exported successfully to " + filePath, 0, 0);
    }

    public QueryResult importDatabase(String dbName, String filePath) throws Exception {
        verifyPrivilege("CREATE", dbName, "*");
        
        File file = new File(filePath);
        if (!file.exists()) {
            return QueryResult.createError("Error: Import file '" + filePath + "' does not exist");
        }
        
        String format = "db";
        int dot = filePath.lastIndexOf('.');
        if (dot >= 0) {
            format = filePath.substring(dot + 1).toLowerCase();
        }
        
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            DatabaseExporter.importDatabase(this, dbName, is, format);
        }
        
        return QueryResult.createSuccess("Database '" + dbName + "' imported successfully from " + filePath, 0, 0);
    }

}
