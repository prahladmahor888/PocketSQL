package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SqlKeywordSuggester — Helper class to suggest SQL keywords whose logic is implemented in PocketSQL.
 */
public class SqlKeywordSuggester {

    private static final List<String> RAW_SUGGESTIONS = Arrays.asList(
        // DML Commands & Modifiers
        "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "VALUES", "INTO", "SET", "DISTINCT", "UNION", "HELP",
        "IGNORE", "ALL", "WITH", "RECURSIVE", "DUPLICATE",
        
        // DDL Commands & Modifiers
        "CREATE", "DATABASE", "TABLE", "DROP", "ALTER", "TRUNCATE", "RENAME", "DESCRIBE", "DESC", "USE", "DELIMITER",
        "CONSTRAINT", "COLUMN", "INDEX", "VIEW", "ADD", "MODIFY", "CHANGE", "FIRST", "AFTER", "ENGINE", "CHARSET",
        
        // Show & Admin Commands
        "SHOW", "DATABASES", "TABLES", "COLUMNS", "FIELDS", "STATUS", "PROCESSLIST", "ERRORS",
        "WARNINGS", "VARIABLES", "SCHEMAS", "GRANTS", "ENGINES", "PLUGINS", "TRIGGERS", "EVENTS",
        "EXPORT", "IMPORT", "FULLTEXT", "SPATIAL", "CHARACTER", "PRAGMA",
        
        // Join Types & Modifiers
        "JOIN", "INNER", "LEFT", "RIGHT", "CROSS", "FULL", "OUTER", "NATURAL", "ON", "USING",
        
        // Clauses & Modifiers
        "ORDER", "BY", "GROUP", "LIMIT", "OFFSET", "ASC", "HAVING", "AS", "ROLLUP", "WITH ROLLUP",
        "OVER", "PARTITION",
        
        // Operators & Conditional Logic
        "AND", "OR", "NOT", "LIKE", "NOT LIKE", "REGEXP", "RLIKE", "IN", "NOT IN", "BETWEEN", "NOT BETWEEN", "IS", "NULL",
        "IS NULL", "IS NOT NULL", "IF", "EXISTS", "NOT EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END",
        "GLOBAL", "SESSION", "NAMES",
        
        // Data Types (Numeric)
        "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT", "BIT", "YEAR", "FLOAT", "DOUBLE", "REAL", "DECIMAL", "NUMERIC", "SIGNED",

        // Data Types (String & Binary)
        "CHAR", "VARCHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT", "BINARY", "VARBINARY", "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB", "JSON", "ENUM",

        // Data Types (Geometry)
        "GEOMETRY", "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION",

        // Data Types (Date & Time)
        "DATE", "TIME", "DATETIME", "TIMESTAMP",

        // Constraints & Defaults & Attributes
        "PRIMARY", "KEY", "AUTO_INCREMENT", "UNIQUE", "DEFAULT", "FOREIGN", "REFERENCES", "CHECK",
        "NOT NULL", "UNSIGNED", "ZEROFILL", "CHARACTER SET", "COLLATE", "COMMENT",
        "ON UPDATE", "ON UPDATE CURRENT_TIMESTAMP", "ON UPDATE CASCADE", "CURRENT_TIMESTAMP",
        "CASCADE", "RESTRICT", "NO ACTION", "SET NULL", "VISIBLE", "INVISIBLE", "VIRTUAL", "STORED", "ALWAYS", "GENERATED",
        
        // User & Privilege Management
        "USER", "IDENTIFIED", "GRANT", "PRIVILEGES", "REVOKE", "FLUSH", "TO", "PASSWORD", "LOCK",
        
        // Transaction Control
        "START", "TRANSACTION", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "READ", "ONLY", "WRITE",
        
        // Stored Programs
        "CALL", "PROCEDURE", "FUNCTION", "TRIGGER", "EVENT", "RETURNS", "RETURN", "DECLARE", "LOOP", "WHILE", "REPEAT", "UNTIL", "LEAVE",

        // Built-in SQL Functions (String & Formatting)
        "CONCAT()", "CONCAT_WS()", "FORMAT()", "UPPER()", "LOWER()", "LENGTH()", "CHAR_LENGTH()", "SUBSTRING()", "SUBSTR()",
        "LEFT()", "RIGHT()", "TRIM()", "LTRIM()", "RTRIM()", "REPLACE()", "REVERSE()", "INSTR()", "LOCATE()", "LPAD()", "RPAD()", "REPEAT()",
        "HEX()", "UNHEX()", "FIELD()", "FIND_IN_SET()", "ELT()", "MAKE_SET()", "QUOTE()", "ASCII()", "CHAR()",
        
        // Built-in SQL Functions (Numeric)
        "ABS()", "ROUND()", "CEIL()", "CEILING()", "FLOOR()", "MOD()", "POWER()", "POW()", "SQRT()", "RAND()", "SIGN()", "PI()", "EXP()", "LOG()",
        "LOG10()", "LOG2()", "DEGREES()", "RADIANS()", "SIN()", "COS()", "TAN()", "ASIN()", "ACOS()", "ATAN()", "TRUNCATE()", "LEAST()", "GREATEST()",
        
        // Built-in SQL Functions (Date & Time)
        "NOW()", "CURDATE()", "CURRENT_DATE()", "CURTIME()", "CURRENT_TIME()", "DATE()", "TIME()", "YEAR()", "MONTH()", "DAY()", "DAYOFMONTH()",
        "HOUR()", "MINUTE()", "SECOND()", "DATEDIFF()", "DATE_ADD()", "DATE_SUB()", "DATE_FORMAT()", "STR_TO_DATE()", "TIMESTAMPDIFF()",
        "ADDDATE()", "SUBDATE()", "EXTRACT()", "LAST_DAY()", "MAKEDATE()", "MAKETIME()", "DAYNAME()", "MONTHNAME()", "WEEK()", "WEEKDAY()",
        
        // Built-in SQL Functions (Conditional & Conversion)
        "IF()", "IFNULL()", "NULLIF()", "COALESCE()", "CAST()", "CONVERT()", "BINARY()",
        
        // Built-in SQL Functions (Encryption & System)
        "MD5()", "SHA1()", "SHA()", "SHA2()", "AES_ENCRYPT()", "AES_DECRYPT()", "DATABASE()", "VERSION()", "CONNECTION_ID()", "SYSTEM_USER()", "SESSION_USER()",
        "USER()", "CURRENT_USER()", "CHARSET()", "COLLATION()",
        
        // Built-in SQL Functions (JSON & Aggregate & Window)
        "JSON_OBJECT()", "JSON_ARRAY()", "JSON_EXTRACT()", "JSON_SET()", "JSON_REMOVE()", "JSON_CONTAINS()",
        "COUNT()", "SUM()", "AVG()", "MIN()", "MAX()", "GROUP_CONCAT()",
        "ROW_NUMBER()", "RANK()", "DENSE_RANK()", "NTILE()", "LAG()", "LEAD()", "FIRST_VALUE()", "LAST_VALUE()"
    );

    // Guaranteed deduplicated list preserving category order
    private static final List<String> SUGGESTIONS;

    static {
        java.util.Set<String> set = new java.util.LinkedHashSet<>(RAW_SUGGESTIONS);
        SUGGESTIONS = Collections.unmodifiableList(new ArrayList<>(set));
    }

    /**
     * Returns matching SQL keywords based on the input prefix (case-insensitive).
     * Guaranteed no duplicates in results.
     *
     * @param prefix Prefix to search for.
     * @return List of matching unique keywords sorted alphabetically.
     */
    public static List<String> suggest(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String prefixLower = prefix.trim().toLowerCase();
        java.util.Set<String> resultSet = new java.util.LinkedHashSet<>();
        for (String kw : SUGGESTIONS) {
            if (kw.toLowerCase().startsWith(prefixLower)) {
                resultSet.add(kw);
            }
        }
        List<String> results = new ArrayList<>(resultSet);
        Collections.sort(results);
        return results;
    }

    /**
     * Gets all keywords whose logic is implemented in PocketSQL (guaranteed unique).
     */
    public static List<String> getKeywords() {
        return SUGGESTIONS;
    }
}
