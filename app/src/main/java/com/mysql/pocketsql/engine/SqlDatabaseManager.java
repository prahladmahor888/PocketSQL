package com.mysql.pocketsql.engine;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlDatabaseManager {
    private final DatabaseEngine engine;

    public SqlDatabaseManager(DatabaseEngine engine) {
        this.engine = engine;
    }

    public QueryResult createDatabase(String dbName, boolean ifNotExists) throws Exception {
        return createDatabase(dbName, ifNotExists, null, null);
    }

    public QueryResult createDatabase(String dbName, boolean ifNotExists, String charset, String collation) throws Exception {
        engine.verifyPrivilege("CREATE", "*", "*");
        if (dbName == null || dbName.trim().isEmpty()) {
            return QueryResult.createError("Error: Database name cannot be empty");
        }
        String resolved = engine.resolveDatabaseName(dbName);
        if (engine.storageEngine.databaseExists(resolved)) {
            if (ifNotExists) {
                return QueryResult.createSuccess("Database already exists (ignored)", 0, 0);
            }
            return QueryResult.createError("ERROR 1007 (HY000): Can't create database '" + dbName + "'; database exists");
        }
        engine.storageEngine.createDatabaseDir(dbName);

        // Save default character set and collation in __db_metadata__
        JSONObject schemaJson = engine.storageEngine.readSchema(dbName);
        JSONObject dbMetadata = new JSONObject();
        
        String finalCharset = charset != null ? charset : "utf8mb4";
        String finalCollation = collation;
        
        if (finalCollation == null) {
            finalCollation = SqlCollation.getDefaultCollationForCharset(finalCharset);
        } else if (charset == null && collation != null) {
            finalCharset = SqlCollation.getCharsetForCollation(finalCollation);
        }
        
        // Validate
        if (!SqlCollation.isValidCharset(finalCharset)) {
            throw new Exception("Error: Unknown character set: '" + finalCharset + "'");
        }
        if (!SqlCollation.isValidCollation(finalCollation)) {
            throw new Exception("Error: Unknown collation: '" + finalCollation + "'");
        }
        
        dbMetadata.put("default_character_set", finalCharset.toLowerCase());
        dbMetadata.put("default_collation", finalCollation.toLowerCase());
        schemaJson.put("__db_metadata__", dbMetadata);
        
        engine.storageEngine.writeSchema(dbName, schemaJson);

        return QueryResult.createSuccess("Database created successfully", 1, 0);
    }

    public QueryResult dropDatabase(String dbName, boolean ifExists) throws Exception {
        engine.verifyPrivilege("DROP", "*", "*");
        dbName = engine.resolveDatabaseName(dbName);
        if (!engine.storageEngine.databaseExists(dbName)) {
            if (ifExists) {
                return QueryResult.createSuccess("Database does not exist (ignored)", 0, 0);
            }
            return QueryResult.createError("ERROR 1008 (HY000): Can't drop database '" + dbName + "'; database doesn't exist");
        }
        
        if (dbName.equals(engine.activeDatabaseName)) {
            engine.activeDatabaseName = null;
            engine.activeSchemaJson = null;
            engine.tableCache.clear();
        }
        
        engine.storageEngine.deleteDatabaseDir(dbName);
        return QueryResult.createSuccess("Database dropped successfully", 1, 0);
    }

    public QueryResult useDatabase(String dbName) throws Exception {
        if (engine.currentUser == null) {
            throw new Exception("ERROR 1045 (28000): Access denied for user");
        }
        dbName = engine.resolveDatabaseName(dbName);
        if (!engine.storageEngine.databaseExists(dbName)) {
            return QueryResult.createError("ERROR 1049 (42000): Unknown database '" + dbName + "'");
        }
        engine.saveDirtyTables();
        engine.activeDatabaseName = dbName;
        engine.tableCache.clear();
        engine.activeSchemaJson = engine.storageEngine.readSchema(dbName);
        return QueryResult.createSuccess("Database changed", 0, 0);
    }

    public QueryResult showDatabases() throws Exception {
        return showDatabases(null, null);
    }

    public QueryResult showDatabases(String likePattern, Clause.Where where) throws Exception {
        if (engine.currentUser == null) {
            throw new Exception("Error: Access denied; you need (at least one of) the USAGE privilege(s) for this operation");
        }
        List<String> dbs = engine.storageEngine.listDatabases();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String db : dbs) {
            if (likePattern != null && !likePattern.trim().isEmpty()) {
                if (!matchesLike(db, likePattern.trim())) {
                    continue;
                }
            }
            Map<String, Object> row = new HashMap<>();
            row.put("Database", db);
            if (where != null && !where.evaluate(row, "utf8mb4_general_ci", engine)) {
                continue;
            }
            rows.add(row);
        }
        return QueryResult.createSelectSuccess(
            Collections.singletonList("Database"),
            Collections.singletonList("TEXT"),
            rows,
            0
        );
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
}
