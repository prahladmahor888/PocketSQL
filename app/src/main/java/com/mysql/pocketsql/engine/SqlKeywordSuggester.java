package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SqlKeywordSuggester — Helper class to suggest SQL keywords whose logic is implemented in PocketSQL.
 */
public class SqlKeywordSuggester {

    private static final List<String> SUGGESTIONS = Arrays.asList(
        // DML Commands
        "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "VALUES", "INTO", "SET", "DISTINCT", "UNION", "HELP",
        
        // DDL Commands
        "CREATE", "DATABASE", "TABLE", "DROP", "ALTER", "TRUNCATE", "RENAME", "DESCRIBE", "DESC", "USE", "DELIMITER",
        "CONSTRAINT", "COLUMN", "INDEX", "VIEW",
        
        // Show Commands
        "SHOW", "DATABASES", "TABLES", "COLUMNS", "FIELDS", "STATUS", "PROCEDURE", "FUNCTION",
        
        // Join Types
        "JOIN", "INNER", "LEFT", "RIGHT", "CROSS", "ON",
        
        // Clauses
        "ORDER", "BY", "GROUP", "LIMIT", "ASC", "HAVING", "AS",
        
        // Operators & Special
        "AND", "OR", "NOT", "LIKE", "IN", "BETWEEN", "IS", "NULL", "IF", "EXISTS",
        "GLOBAL", "SESSION", "NAMES",
        
        // Data Types (Numeric)
        "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT", "BIT", "YEAR", "FLOAT", "DOUBLE", "REAL", "DECIMAL", "NUMERIC",

        // Data Types (String & Binary)
        "CHAR", "VARCHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT", "BINARY", "VARBINARY", "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB", "JSON", "ENUM", "SET",

        // Data Types (Geometry)
        "GEOMETRY", "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION",

        // Data Types (Date & Time)
        "DATE", "TIME", "DATETIME", "TIMESTAMP",

        // Constraints & Defaults & Attributes
        "PRIMARY", "KEY", "AUTO_INCREMENT", "UNIQUE", "DEFAULT", "FOREIGN", "REFERENCES", "CHECK",
        "NOT NULL", "UNSIGNED", "ZEROFILL", "CHARACTER SET", "COLLATE",
        "ON UPDATE", "ON UPDATE CURRENT_TIMESTAMP", "ON UPDATE CASCADE", "CURRENT_TIMESTAMP",
        
        // User & Privilege Management
        "USER", "IDENTIFIED", "BY", "GRANT", "PRIVILEGES", "REVOKE", "FLUSH", "TO",
        
        // Transaction Control
        "START", "TRANSACTION", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT",
        
        // Stored Programs
        "CALL", "PROCEDURE", "FUNCTION", "TRIGGER", "EVENT",

        // Built-in SQL Functions (String)
        "CONCAT()", "CONCAT_WS()", "UPPER()", "LOWER()", "LENGTH()", "CHAR_LENGTH()", "SUBSTRING()", "SUBSTR()",
        "LEFT()", "RIGHT()", "TRIM()", "LTRIM()", "RTRIM()", "REPLACE()", "REVERSE()", "INSTR()", "LOCATE()", "LPAD()", "RPAD()", "REPEAT()",
        
        // Built-in SQL Functions (Numeric)
        "ABS()", "ROUND()", "CEIL()", "CEILING()", "FLOOR()", "MOD()", "POWER()", "POW()", "SQRT()", "RAND()", "SIGN()", "PI()", "EXP()", "LOG()",
        
        // Built-in SQL Functions (Date & Time)
        "NOW()", "CURDATE()", "CURRENT_DATE()", "CURTIME()", "CURRENT_TIME()", "DATE()", "TIME()", "YEAR()", "MONTH()", "DAY()", "DAYOFMONTH()",
        "HOUR()", "MINUTE()", "SECOND()", "DATEDIFF()", "DATE_ADD()", "DATE_SUB()", "TIMESTAMPDIFF()",
        
        // Built-in SQL Functions (Conditional & Conversion)
        "IF()", "IFNULL()", "NULLIF()", "COALESCE()", "CAST()", "CONVERT()", "BINARY()",
        
        // Built-in SQL Functions (Encryption & System)
        "MD5()", "SHA1()", "SHA()", "SHA2()", "AES_ENCRYPT()", "AES_DECRYPT()", "DATABASE()", "VERSION()", "CONNECTION_ID()", "SYSTEM_USER()", "SESSION_USER()",
        
        // Built-in SQL Functions (JSON & Aggregate)
        "JSON_OBJECT()", "JSON_ARRAY()", "JSON_EXTRACT()", "JSON_SET()", "JSON_REMOVE()", "COUNT()", "SUM()", "AVG()", "MIN()", "MAX()", "GROUP_CONCAT()"
    );

    /**
     * Returns matching SQL keywords based on the input prefix (case-insensitive).
     *
     * @param prefix Prefix to search for.
     * @return List of matching keywords sorted alphabetically.
     */
    public static List<String> suggest(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String prefixLower = prefix.trim().toLowerCase();
        List<String> results = new ArrayList<>();
        for (String kw : SUGGESTIONS) {
            if (kw.toLowerCase().startsWith(prefixLower)) {
                results.add(kw);
            }
        }
        Collections.sort(results);
        return results;
    }

    /**
     * Gets all keywords whose logic is implemented in PocketSQL.
     */
    public static List<String> getKeywords() {
        return Collections.unmodifiableList(SUGGESTIONS);
    }
}
