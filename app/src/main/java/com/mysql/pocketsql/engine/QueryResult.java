package com.mysql.pocketsql.engine;

import java.util.List;
import java.util.Map;

public class QueryResult {
    public final boolean success;
    public final String message;
    public final List<String> columns;
    public final List<String> columnTypes;
    public final List<Map<String, Object>> rows;
    public final int affectedRows;
    public final long executionTimeMs;

    public QueryResult(boolean success, String message, List<String> columns, List<String> columnTypes, List<Map<String, Object>> rows, int affectedRows, long executionTimeMs) {
        this.success = success;
        this.message = message;
        this.columns = columns;
        this.columnTypes = columnTypes;
        this.rows = rows;
        this.affectedRows = affectedRows;
        this.executionTimeMs = executionTimeMs;
    }

    public static QueryResult createSuccess(String message, int affectedRows, long executionTimeMs) {
        return new QueryResult(true, message, null, null, null, affectedRows, executionTimeMs);
    }

    public static QueryResult createSelectSuccess(List<String> columns, List<String> columnTypes, List<Map<String, Object>> rows, long executionTimeMs) {
        String msg = rows.size() + " row" + (rows.size() == 1 ? "" : "s") + " in set";
        return new QueryResult(true, msg, columns, columnTypes, rows, 0, executionTimeMs);
    }

    public static QueryResult createError(String message) {
        return new QueryResult(false, message, null, null, null, 0, 0);
    }
}
