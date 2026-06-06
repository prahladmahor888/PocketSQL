package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SqlConstraint — SQL Constraint Model aur Validation Engine
 *
 * MySQL me constraints data integrity ensure karte hain:
 *
 *   NOT NULL       — Column null nahi ho sakta
 *   DEFAULT        — Insert ke time default value use hoti hai
 *   UNIQUE         — Duplicate values nahi ho sakte
 *   PRIMARY KEY    — Unique + NOT NULL combined (row identifier)
 *   FOREIGN KEY    — Parent table se referential integrity
 *   CHECK          — Custom validation condition
 *   AUTO_INCREMENT — Automatic numeric value generation
 *
 * Ye class inke models aur ek centralized validator define karti hai
 * jo DatabaseEngine ke validateRowConstraints(...) ko clean rakhti hai.
 */
public class SqlConstraint {

    // ──────────────────────────────────────────────────────────
    // Constraint type enum
    // ──────────────────────────────────────────────────────────

    public enum Type {
        NOT_NULL,
        DEFAULT,
        UNIQUE,
        PRIMARY_KEY,
        FOREIGN_KEY,
        CHECK,
        AUTO_INCREMENT
    }

    // ──────────────────────────────────────────────────────────
    // NotNullConstraint — column me null value nahi aa sakti
    // ──────────────────────────────────────────────────────────

    public static class NotNullConstraint {
        public final String column;

        public NotNullConstraint(String column) {
            this.column = column;
        }

        public void validate(Map<String, Object> row) throws Exception {
            if (row.get(column) == null) {
                throw new Exception("Column '" + column + "' cannot be null");
            }
        }

        @Override
        public String toString() {
            return column + " NOT NULL";
        }
    }

    // ──────────────────────────────────────────────────────────
    // DefaultConstraint — column ka default value
    // ──────────────────────────────────────────────────────────

    public static class DefaultConstraint {
        public final String column;
        public final Object defaultValue;

        public DefaultConstraint(String column, Object defaultValue) {
            this.column       = column;
            this.defaultValue = defaultValue;
        }

        /** Row me agar column absent hai toh default value apply karo */
        public void applyIfAbsent(Map<String, Object> row) {
            if (!row.containsKey(column) || row.get(column) == null) {
                row.put(column, defaultValue);
            }
        }

        @Override
        public String toString() {
            return column + " DEFAULT " + defaultValue;
        }
    }

    // ──────────────────────────────────────────────────────────
    // CheckConstraint — user-defined validation rule
    // ──────────────────────────────────────────────────────────

    public static class CheckConstraint {
        public final String column;
        public final String operator;
        public final Object value;        // For simple comparisons
        public final List<Object> values; // For IN / BETWEEN

        /** Simple comparison constructor (=, !=, >, <, >=, <=) */
        public CheckConstraint(String column, String operator, Object value) {
            this.column   = column;
            this.operator = operator.toUpperCase();
            this.value    = value;
            this.values   = null;
        }

        /** Multi-value constructor (IN, BETWEEN) */
        public CheckConstraint(String column, String operator, List<Object> values) {
            this.column   = column;
            this.operator = operator.toUpperCase();
            this.value    = null;
            this.values   = values;
        }

        /**
         * Row ke column value ko constraint ke against validate karta hai.
         */
        public boolean validate(Map<String, Object> row) {
            Object rowVal = row.get(column);
            if (rowVal == null) return true; // NULL values skip hote hain CHECK ke liye

            switch (operator) {
                case "BETWEEN":
                    return (values != null && values.size() == 2)
                        && SqlOperator.evaluateBetween(rowVal, values.get(0), values.get(1));

                case "IN":
                    return SqlOperator.evaluateIn(rowVal, values);

                default:
                    return SqlOperator.compare(rowVal, operator, value);
            }
        }

        @Override
        public String toString() {
            return "CHECK (" + column + " " + operator + " " + (values != null ? values : value) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // UniqueConstraint — column group me duplicate nahi hone chahiye
    // ──────────────────────────────────────────────────────────

    public static class UniqueConstraint {
        public final List<String> columns;
        public final String keyName;

        public UniqueConstraint(List<String> columns, String keyName) {
            this.columns = columns;
            this.keyName = keyName;
        }

        /**
         * Existing rows ke against uniqueness check karta hai.
         *
         * @param newRow       New row jo insert ho rahi hai
         * @param existingRows Table ke baaki rows
         * @param originalRow  UPDATE ke case me original row (skip karna hai)
         */
        public void validate(Map<String, Object> newRow,
                             List<Map<String, Object>> existingRows,
                             Map<String, Object> originalRow,
                             String tableName,
                             JSONObject tableSchema,
                             DatabaseEngine engine) throws Exception {
            // NULL ho toh uniqueness check skip
            for (String col : columns) {
                if (newRow.get(col) == null) return;
            }

            TableData td = engine.getOrLoadTable(tableName);
            TableData.UniqueIndex index = td.getOrCreateUniqueIndex(columns, tableName, engine);

            List<Object> key = td.makeIndexKey(newRow, columns, index.collations);
            if (key != null) {
                if (index.keys.contains(key)) {
                    boolean isDuplicate = true;
                    if (originalRow != null) {
                        List<Object> origKey = td.makeIndexKey(originalRow, columns, index.collations);
                        if (key.equals(origKey)) {
                            isDuplicate = false; // same row, same key => allowed
                        }
                    }
                    if (isDuplicate) {
                        StringBuilder valBuilder = new StringBuilder();
                        for (int j = 0; j < columns.size(); j++) {
                            String col = columns.get(j);
                            Object newVal = newRow.get(col);
                            if (j > 0) valBuilder.append("-");
                            valBuilder.append(newVal != null ? newVal.toString() : "null");
                        }
                        String name = (keyName != null && !keyName.isEmpty()) ? keyName : columns.get(0);
                        throw new Exception("Duplicate entry '" + valBuilder + "' for key '" + name + "'");
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "UNIQUE (" + String.join(", ", columns) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // PrimaryKeyConstraint — table ka main unique identifier
    // ──────────────────────────────────────────────────────────

    public static class PrimaryKeyConstraint {
        public final List<String> columns;

        public PrimaryKeyConstraint(List<String> columns) {
            this.columns = columns;
        }

        /**
         * Primary key uniqueness validate karta hai.
         */
        public void validate(Map<String, Object> newRow,
                             List<Map<String, Object>> existingRows,
                             Map<String, Object> originalRow,
                             String tableName,
                             JSONObject tableSchema,
                             DatabaseEngine engine) throws Exception {
            if (columns.isEmpty()) return;

            TableData td = engine.getOrLoadTable(tableName);
            TableData.UniqueIndex index = td.getOrCreateUniqueIndex(columns, tableName, engine);

            List<Object> key = td.makeIndexKey(newRow, columns, index.collations);
            if (key != null) {
                if (index.keys.contains(key)) {
                    boolean isDuplicate = true;
                    if (originalRow != null) {
                        List<Object> origKey = td.makeIndexKey(originalRow, columns, index.collations);
                        if (key.equals(origKey)) {
                            isDuplicate = false; // same row, same key => allowed
                        }
                    }
                    if (isDuplicate) {
                        StringBuilder valBuilder = new StringBuilder();
                        for (int j = 0; j < columns.size(); j++) {
                            String col = columns.get(j);
                            Object newVal = newRow.get(col);
                            if (j > 0) valBuilder.append("-");
                            valBuilder.append(newVal != null ? newVal.toString() : "null");
                        }
                        throw new Exception("Duplicate entry '" + valBuilder + "' for key 'PRIMARY'");
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "PRIMARY KEY (" + String.join(", ", columns) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // ForeignKeyConstraint — parent table se referential integrity
    // ──────────────────────────────────────────────────────────

    public static class ForeignKeyConstraint {
        public final String column;
        public final String parentTable;
        public final String parentColumn;

        public ForeignKeyConstraint(String column, String parentTable, String parentColumn) {
            this.column       = column;
            this.parentTable  = parentTable;
            this.parentColumn = parentColumn;
        }

        public void validate(Map<String, Object> row, String tableName, String databaseName, DatabaseEngine engine) throws Exception {
            Object newVal = row.get(column);
            if (newVal != null) {
                TableData parentTd = engine.getOrLoadTable(parentTable);
                boolean found = false;
                String collation = engine.getColumnCollation(parentTable, parentColumn);
                for (Map<String, Object> parentRow : parentTd.rows) {
                    Object parentVal = parentRow.get(parentColumn);
                    if (parentVal != null && compareEqual(newVal, parentVal, collation)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new Exception("Cannot add or update a child row: a foreign key constraint fails (" + databaseName + "." + tableName + ", CONSTRAINT fk_" + column + " FOREIGN KEY (" + column + ") REFERENCES " + parentTable + " (" + parentColumn + "))");
                }
            }
        }

        @Override
        public String toString() {
            return "FOREIGN KEY (" + column + ") REFERENCES " + parentTable + "(" + parentColumn + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // AutoIncrementConstraint — auto numeric generation
    // ──────────────────────────────────────────────────────────

    public static class AutoIncrementConstraint {
        public final String column;

        public AutoIncrementConstraint(String column) {
            this.column = column;
        }

        @Override
        public String toString() {
            return column + " AUTO_INCREMENT";
        }
    }

    // ──────────────────────────────────────────────────────────
    // Factory: JSON table schema se constraints build karo
    // ──────────────────────────────────────────────────────────

    /**
     * Table schema JSONObject se CheckConstraint list build karta hai.
     * DatabaseEngine.validateRowConstraints(...) me use hota hai.
     */
    public static List<CheckConstraint> buildChecks(JSONArray checksArr) {
        List<CheckConstraint> result = new ArrayList<>();
        if (checksArr == null) return result;
        for (int i = 0; i < checksArr.length(); i++) {
            try {
                JSONObject chk = checksArr.getJSONObject(i);
                String col = chk.getString("column");
                String op  = chk.getString("operator").toUpperCase();

                if ("BETWEEN".equals(op)) {
                    JSONArray range = chk.getJSONArray("values");
                    List<Object> vals = new ArrayList<>();
                    vals.add(range.get(0));
                    vals.add(range.get(1));
                    result.add(new CheckConstraint(col, op, vals));

                } else if ("IN".equals(op)) {
                    JSONArray allowed = chk.getJSONArray("values");
                    List<Object> vals = new ArrayList<>();
                    for (int j = 0; j < allowed.length(); j++) {
                        vals.add(allowed.get(j));
                    }
                    result.add(new CheckConstraint(col, op, vals));

                } else {
                    Object checkVal = chk.has("value") ? chk.get("value") : null;
                    result.add(new CheckConstraint(col, op, checkVal));
                }
            } catch (Exception ignored) { }
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────
    // Centralized row constraints validator
    // ──────────────────────────────────────────────────────────

    public static void validateRow(String tableName, Map<String, Object> row, TableData td, Map<String, Object> originalRow, JSONObject tableSchema, DatabaseEngine engine) throws Exception {
        if (!engine.isConstraintsEnabled()) return;
        // 1. NOT NULL constraint check
        for (int i = 0; i < td.columns.size(); i++) {
            String col = td.columns.get(i);
            if (!engine.isColumnNullable(tableSchema, col)) {
                new NotNullConstraint(col).validate(row);
            }
        }

        // 2. UNIQUE / PRIMARY KEY duplicate check (including composite keys)
        List<String> pkCols = new ArrayList<>();
        JSONArray pkArr = tableSchema.optJSONArray("primary_key");
        if (pkArr != null) {
            for (int j = 0; j < pkArr.length(); j++) {
                pkCols.add(pkArr.getString(j));
            }
        } else {
            // Fallback for older schemas: collect columns marked as PRI
            for (String col : td.columns) {
                if ("PRI".equalsIgnoreCase(engine.getColumnKeyType(tableSchema, col))) {
                    pkCols.add(col);
                }
            }
        }

        if (!pkCols.isEmpty()) {
            new PrimaryKeyConstraint(pkCols).validate(row, td.rows, originalRow, tableName, tableSchema, engine);
        }

        List<List<String>> uniqueGroups = new ArrayList<>();
        JSONArray uniArr = tableSchema.optJSONArray("uniques");
        if (uniArr != null) {
            for (int j = 0; j < uniArr.length(); j++) {
                JSONArray groupArr = uniArr.getJSONArray(j);
                List<String> group = new ArrayList<>();
                for (int k = 0; k < groupArr.length(); k++) {
                    group.add(groupArr.getString(k));
                }
                uniqueGroups.add(group);
            }
        } else {
            // Fallback for older schemas: collect columns marked as UNI
            for (String col : td.columns) {
                if ("UNI".equalsIgnoreCase(engine.getColumnKeyType(tableSchema, col))) {
                    List<String> group = new ArrayList<>();
                    group.add(col);
                    uniqueGroups.add(group);
                }
            }
        }

        for (List<String> group : uniqueGroups) {
            if (group.isEmpty()) continue;
            new UniqueConstraint(group, group.get(0)).validate(row, td.rows, originalRow, tableName, tableSchema, engine);
        }

        // 3. FOREIGN KEY check
        JSONObject fkObj = tableSchema.optJSONObject("foreign_keys");
        if (fkObj != null && engine.isForeignKeyChecksEnabled()) {
            Iterator<String> keys = fkObj.keys();
            while (keys.hasNext()) {
                String fkCol = keys.next();
                Object newVal = row.get(fkCol);
                if (newVal != null) {
                    Object refObj = fkObj.get(fkCol);
                    if (refObj == JSONObject.NULL) continue;
                    String refStr = refObj.toString(); // e.g. "users.id"
                    if (refStr.trim().isEmpty()) continue;
                    int dot = refStr.indexOf('.');
                    if (dot == -1) continue;
                    String parentTable = refStr.substring(0, dot);
                    String parentCol = refStr.substring(dot + 1);

                    new ForeignKeyConstraint(fkCol, parentTable, parentCol).validate(row, tableName, engine.activeDatabaseName, engine);
                }
            }
        }

        // 4. CHECK constraint check
        JSONArray checksArr = tableSchema.optJSONArray("checks");
        List<CheckConstraint> checks = buildChecks(checksArr);
        for (int i = 0; i < checks.size(); i++) {
            if (!checks.get(i).validate(row)) {
                throw new Exception("Check constraint '" + tableName + "_chk_" + (i + 1) + "' is violated.");
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // Private helper: two values equal hain ya nahi
    // ──────────────────────────────────────────────────────────

    private static boolean compareEqual(Object v1, Object v2) {
        return compareEqual(v1, v2, "utf8mb4_0900_ai_ci");
    }

    private static boolean compareEqual(Object v1, Object v2, String collation) {
        if (v1 == null || v2 == null) return false;
        return SqlOperator.compare(v1, "=", v2, collation);
    }
}
