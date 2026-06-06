package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlHelpManager {

    private static final Map<String, String> HELP_MAP = new HashMap<>();

    static {
        // DML
        HELP_MAP.put("SELECT", "Retrieves data from one or more tables. Usage: SELECT columns FROM table WHERE condition LIMIT count;");
        HELP_MAP.put("INSERT", "Adds new rows of data into a table. Usage: INSERT INTO table (cols) VALUES (vals);");
        HELP_MAP.put("UPDATE", "Modifies existing data in a table. Usage: UPDATE table SET col=val WHERE condition;");
        HELP_MAP.put("DELETE", "Removes rows of data from a table. Usage: DELETE FROM table WHERE condition;");
        HELP_MAP.put("FROM", "Specifies the source table(s) to retrieve data from in a SELECT statement.");
        HELP_MAP.put("WHERE", "Filters rows returned by a query based on a specified condition.");
        HELP_MAP.put("VALUES", "Specifies values to insert into table columns in an INSERT statement.");
        HELP_MAP.put("INTO", "Specifies target table for INSERT operations.");
        HELP_MAP.put("SET", "Assigns values to columns in an UPDATE statement, or sets variables.");
        HELP_MAP.put("DISTINCT", "Removes duplicate rows from query results. Usage: SELECT DISTINCT col FROM tbl;");
        HELP_MAP.put("UNION", "Combines results of two or more queries. Usage: SELECT ... UNION SELECT ...;");

        // DDL
        HELP_MAP.put("CREATE DATABASE", "Creates a new database directory. Usage: CREATE DATABASE dbName;");
        HELP_MAP.put("CREATE TABLE", "Creates a new table with column definitions. Usage: CREATE TABLE tbl (col Type constraints);");
        HELP_MAP.put("DROP DATABASE", "Drops/removes an existing database. Usage: DROP DATABASE dbName;");
        HELP_MAP.put("DROP TABLE", "Drops/removes an existing table. Usage: DROP TABLE tblName;");
        HELP_MAP.put("ALTER TABLE", "Modifies an existing table's columns or constraints. Usage: ALTER TABLE tbl ADD/DROP/MODIFY column;");
        HELP_MAP.put("TRUNCATE", "Empties all data rows from a table. Usage: TRUNCATE TABLE tblName;");
        HELP_MAP.put("RENAME", "Renames a table or database. Usage: RENAME TABLE old TO new;");
        HELP_MAP.put("DESCRIBE", "Displays the structure/schema of a table. Usage: DESCRIBE tblName;");
        HELP_MAP.put("DESC", "Short alias for DESCRIBE. Displays the structure of a table. Usage: DESC tblName;");
        HELP_MAP.put("USE", "Sets the active database context. Usage: USE dbName;");
        HELP_MAP.put("DELIMITER", "Changes the statement query terminator/delimiter. Usage: DELIMITER //");
        HELP_MAP.put("CONSTRAINT", "Declares a named constraint (primary/foreign/check/unique) in ALTER or CREATE TABLE.");
        HELP_MAP.put("COLUMN", "Specifies column-level actions in ALTER TABLE commands.");
        HELP_MAP.put("INDEX", "Creates or drops an index on table columns. Usage: CREATE INDEX idx ON tbl(col);");

        // Show
        HELP_MAP.put("SHOW", "Displays metadata (databases, tables, columns, function status, etc.).");
        HELP_MAP.put("SHOW DATABASES", "Lists all existing databases. Usage: SHOW DATABASES;");
        HELP_MAP.put("SHOW TABLES", "Lists all tables in the active database. Usage: SHOW TABLES;");
        HELP_MAP.put("SHOW COLUMNS", "Lists column metadata of a table. Usage: SHOW COLUMNS FROM tbl;");
        HELP_MAP.put("SHOW CREATE TABLE", "Displays the CREATE TABLE statement used to create a table.");
        HELP_MAP.put("SHOW CREATE DATABASE", "Displays the CREATE DATABASE statement used to create a database.");

        // Join
        HELP_MAP.put("JOIN", "Combines rows from two or more tables based on a related column between them.");
        HELP_MAP.put("INNER JOIN", "Returns rows that have matching values in both tables.");
        HELP_MAP.put("LEFT JOIN", "Returns all rows from left table, and matched rows from right table.");
        HELP_MAP.put("RIGHT JOIN", "Returns all rows from right table, and matched rows from left table.");
        HELP_MAP.put("CROSS JOIN", "Returns the Cartesian product of rows from both joined tables.");
        HELP_MAP.put("ON", "Specifies the condition for combining rows in a JOIN statement.");

        // Clauses
        HELP_MAP.put("ORDER BY", "Sorts query results in ascending or descending order. Usage: ORDER BY col ASC/DESC;");
        HELP_MAP.put("GROUP BY", "Groups rows that have same values into summary rows. Usage: GROUP BY col;");
        HELP_MAP.put("LIMIT", "Limits number of rows returned by a query. Usage: LIMIT count;");
        HELP_MAP.put("ASC", "Specifies ascending sort order for ORDER BY.");
        HELP_MAP.put("HAVING", "Filters grouped rows returned by a GROUP BY clause.");
        HELP_MAP.put("AS", "Renames columns or tables with an alias for query duration.");

        // Operators & Special
        HELP_MAP.put("AND", "Logical operator returning true if all conditions are true.");
        HELP_MAP.put("OR", "Logical operator returning true if any condition is true.");
        HELP_MAP.put("NOT", "Logical operator negating a boolean expression.");
        HELP_MAP.put("LIKE", "Matches patterns in string values using wildcards (% and _).");
        HELP_MAP.put("IN", "Filters values matching any value in a list or subquery.");
        HELP_MAP.put("BETWEEN", "Filters values within a range (inclusive). Usage: BETWEEN low AND high;");
        HELP_MAP.put("IS NULL", "Checks if a column value is NULL.");
        HELP_MAP.put("EXISTS", "Checks if a subquery returns any rows.");
        HELP_MAP.put("GLOBAL", "Sets system variables globally.");
        HELP_MAP.put("SESSION", "Sets system variables for the current session only.");

        // Constraints & Defaults & Attributes
        HELP_MAP.put("PRIMARY KEY", "Uniquely identifies each row in a table. Consists of NOT NULL unique columns.");
        HELP_MAP.put("AUTO_INCREMENT", "Automatically generates unique sequential numbers for new rows.");
        HELP_MAP.put("UNIQUE", "Ensures all values in a column or column group are distinct.");
        HELP_MAP.put("DEFAULT", "Sets a default fallback value for a column if none is supplied.");
        HELP_MAP.put("FOREIGN KEY", "Constraint linking data in two tables. Usage: FOREIGN KEY (col) REFERENCES parent(col);");
        HELP_MAP.put("REFERENCES", "Specifies parent table and columns for a foreign key constraint.");
        HELP_MAP.put("CHECK", "Validates that values satisfy a conditional expression. Usage: CHECK (col > 0);");
        HELP_MAP.put("NOT NULL", "Prevents NULL values from being stored in a column.");
        HELP_MAP.put("UNSIGNED", "Column attribute preventing negative values in numeric columns.");
        HELP_MAP.put("ZEROFILL", "Column attribute padding numeric values with leading zeros to display size.");
        HELP_MAP.put("ON UPDATE CURRENT_TIMESTAMP", "Automatically updates column to current time when row modifications occur.");
        HELP_MAP.put("CURRENT_TIMESTAMP", "Resolves to the current date and time value.");

        // User & Privilege
        HELP_MAP.put("CREATE USER", "Creates a new user. Usage: CREATE USER username@host IDENTIFIED BY 'password';");
        HELP_MAP.put("GRANT", "Grants specified privileges to a user. Usage: GRANT SELECT ON db.* TO username@host;");
        HELP_MAP.put("REVOKE", "Revokes specified privileges from a user. Usage: REVOKE SELECT ON db.* FROM username@host;");
        HELP_MAP.put("FLUSH PRIVILEGES", "Reloads all user privileges from disk. Usage: FLUSH PRIVILEGES;");
        HELP_MAP.put("EXPORT", "Exports a database structure and data to a file. Usage: EXPORT DATABASE db_name TO '/path/to/file.ext'; (Supported extensions: .sql, .db, .xlsx, .csv)");
        HELP_MAP.put("IMPORT", "Imports a database structure and data from a backup file. Usage: IMPORT DATABASE db_name FROM '/path/to/file.ext'; (Supported extensions: .sql, .db, .xlsx, .csv)");

        // Transaction
        HELP_MAP.put("START TRANSACTION", "Starts a new transaction block. Usage: START TRANSACTION;");
        HELP_MAP.put("BEGIN", "Starts a new transaction block (alias for START TRANSACTION).");
        HELP_MAP.put("COMMIT", "Saves all changes made during the current transaction permanently.");
        HELP_MAP.put("ROLLBACK", "Reverts all changes since the active transaction started.");
        HELP_MAP.put("SAVEPOINT", "Creates a point within a transaction to roll back to. Usage: SAVEPOINT name;");

        // Types
        HELP_MAP.put("INT", "Standard 4-byte integer numeric data type.");
        HELP_MAP.put("VARCHAR", "Variable-length character string data type. Usage: VARCHAR(size);");
        HELP_MAP.put("TEXT", "Character string data type for storing long text.");
        HELP_MAP.put("DECIMAL", "Exact numeric data type for fractional values. Usage: DECIMAL(precision, scale);");
        HELP_MAP.put("DATETIME", "Date and time tracking data type (YYYY-MM-DD HH:MM:SS).");
        HELP_MAP.put("TIMESTAMP", "Date and time tracking data type automatically synced with timezones.");

        // Functions
        HELP_MAP.put("CONCAT()", "Concatenates two or more string values. Usage: CONCAT(str1, str2, ...);");
        HELP_MAP.put("UPPER()", "Converts string to uppercase. Usage: UPPER(str);");
        HELP_MAP.put("LOWER()", "Converts string to lowercase. Usage: LOWER(str);");
        HELP_MAP.put("NOW()", "Returns the current date and time. Usage: NOW();");
        HELP_MAP.put("CURDATE()", "Returns the current date. Usage: CURDATE();");
        HELP_MAP.put("CURTIME()", "Returns the current time. Usage: CURTIME();");
        HELP_MAP.put("ABS()", "Returns absolute value of a number. Usage: ABS(num);");
        HELP_MAP.put("ROUND()", "Rounds a number to specified decimal places. Usage: ROUND(num, decimals);");
        HELP_MAP.put("IF()", "Returns one value if condition is true, another if false. Usage: IF(cond, trueVal, falseVal);");
        HELP_MAP.put("IFNULL()", "Returns alternate value if expression evaluates to NULL. Usage: IFNULL(val, alt);");
        HELP_MAP.put("COALESCE()", "Returns the first non-NULL value in a list of expressions. Usage: COALESCE(v1, v2, ...);");
        HELP_MAP.put("COUNT()", "Aggregate function returning count of rows matching criteria. Usage: COUNT(*);");
        HELP_MAP.put("SUM()", "Aggregate function returning sum of numeric values. Usage: SUM(column);");
        HELP_MAP.put("AVG()", "Aggregate function returning average value of numeric column. Usage: AVG(column);");
        HELP_MAP.put("MIN()", "Aggregate function returning minimum column value. Usage: MIN(column);");
        HELP_MAP.put("MAX()", "Aggregate function returning maximum column value. Usage: MAX(column);");
    }

    public static QueryResult getHelp(String topic) {
        List<String> columns = Arrays.asList("Keyword", "Description");
        List<String> types = Arrays.asList("TEXT", "TEXT");
        List<Map<String, Object>> rows = new ArrayList<>();

        if (topic == null || topic.trim().isEmpty()) {
            // Show all help topics sorted alphabetically
            List<String> sortedKeys = new ArrayList<>(HELP_MAP.keySet());
            Collections.sort(sortedKeys);
            for (String key : sortedKeys) {
                Map<String, Object> row = new HashMap<>();
                row.put("Keyword", key);
                row.put("Description", HELP_MAP.get(key));
                rows.add(row);
            }
        } else {
            String cleanTopic = topic.trim().toUpperCase();
            // Try direct match
            String desc = HELP_MAP.get(cleanTopic);
            if (desc == null) {
                // Try case-insensitive substring/prefix match
                List<String> matches = new ArrayList<>();
                for (String key : HELP_MAP.keySet()) {
                    if (key.toUpperCase().contains(cleanTopic)) {
                        matches.add(key);
                    }
                }
                if (!matches.isEmpty()) {
                    Collections.sort(matches);
                    for (String key : matches) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("Keyword", key);
                        row.put("Description", HELP_MAP.get(key));
                        rows.add(row);
                    }
                } else {
                    Map<String, Object> row = new HashMap<>();
                    row.put("Keyword", topic);
                    row.put("Description", "No help available for this topic. Type 'HELP' to list all topics.");
                    rows.add(row);
                }
            } else {
                Map<String, Object> row = new HashMap<>();
                row.put("Keyword", cleanTopic);
                row.put("Description", desc);
                rows.add(row);
            }
        }

        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }
}
