package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlHelpManager {

    private static final Map<String, String> HELP_MAP = new HashMap<>();

    static {
        // DML
        HELP_MAP.put("SELECT", "Retrieves data rows and columns from one or more tables, views, or cross-database references. Supports filtering (WHERE), sorting (ORDER BY), grouping (GROUP BY), aggregations, joins, and pagination.\nSyntax: SELECT [DISTINCT] columns FROM table [JOIN ...] [WHERE condition] [GROUP BY col [HAVING cond]] [ORDER BY col ASC|DESC] [LIMIT count];\nExample: SELECT id, name FROM users WHERE status = 'active' ORDER BY name ASC LIMIT 10;\nCross-DB: SELECT u.name, o.total FROM users u JOIN shop_db.orders o ON u.id = o.user_id;");
        HELP_MAP.put("INSERT", "Adds new data records into a specified table. Supports single or multi-row insertions.\nSyntax: INSERT INTO table_name (col1, col2, ...) VALUES (val1, val2, ...), (val3, val4, ...);\nExample: INSERT INTO users (name, email, status) VALUES ('Alice', 'alice@test.com', 'active'), ('Bob', 'bob@test.com', 'pending');");
        HELP_MAP.put("UPDATE", "Modifies existing column values in table records matching a specified condition.\nSyntax: UPDATE table_name SET col1 = val1, col2 = val2 WHERE condition;\nExample: UPDATE users SET status = 'verified', login_count = login_count + 1 WHERE id = 42;");
        HELP_MAP.put("DELETE", "Removes matching records permanently from a table.\nSyntax: DELETE FROM table_name WHERE condition;\nExample: DELETE FROM sessions WHERE expired_at < NOW();");
        HELP_MAP.put("FROM", "Specifies the source table(s) or view(s) for data retrieval in a SELECT statement. Supports cross-database table references using db_name.table_name syntax.\nExample: SELECT * FROM analytics_db.page_views;");
        HELP_MAP.put("WHERE", "Filters result rows based on logical conditions (AND, OR, NOT, LIKE, IN, BETWEEN, IS NULL, EXISTS).\nExample: SELECT * FROM products WHERE price >= 10.0 AND stock_quantity > 0;");
        HELP_MAP.put("VALUES", "Specifies literal data values to be inserted into corresponding table columns during an INSERT operation.\nExample: INSERT INTO categories (name) VALUES ('Electronics'), ('Books');");
        HELP_MAP.put("INTO", "Specifies the target table name in an INSERT INTO statement.");
        HELP_MAP.put("SET", "Assigns new values to target columns in an UPDATE statement, or configures session system variables.\nExample: UPDATE settings SET theme = 'dark' WHERE user_id = 1; SET @my_var = 100;");
        HELP_MAP.put("DISTINCT", "Eliminates duplicate result rows from query output based on selected columns.\nSyntax: SELECT DISTINCT column1, column2 FROM table_name;\nExample: SELECT DISTINCT category FROM products;");
        HELP_MAP.put("UNION", "Combines the result sets of two or more SELECT queries into a single output. UNION removes duplicates; UNION ALL keeps all rows.\nSyntax: SELECT col FROM tbl1 UNION SELECT col FROM tbl2;");

        // DDL
        HELP_MAP.put("CREATE DATABASE", "Creates a new isolated database context and storage directory.\nSyntax: CREATE DATABASE database_name;\nExample: CREATE DATABASE e_commerce;");
        HELP_MAP.put("CREATE TABLE", "Defines a new relational table schema specifying columns, data types, and integrity constraints (PRIMARY KEY, FOREIGN KEY, UNIQUE, NOT NULL, CHECK, DEFAULT, AUTO_INCREMENT).\nSyntax: CREATE TABLE table_name (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, price DECIMAL(10,2) DEFAULT 0.00);\nExample: CREATE TABLE orders (id INT PRIMARY KEY, user_id INT, status VARCHAR(20), FOREIGN KEY (user_id) REFERENCES users(id));");
        HELP_MAP.put("DROP DATABASE", "Permanently deletes a database directory along with all its tables, views, triggers, and stored procedures.\nSyntax: DROP DATABASE [IF EXISTS] database_name;\nExample: DROP DATABASE IF EXISTS temp_testing_db;");
        HELP_MAP.put("DROP TABLE", "Permanently deletes a table schema and all contained data records from storage.\nSyntax: DROP TABLE [IF EXISTS] table_name;\nExample: DROP TABLE IF EXISTS old_logs;");
        HELP_MAP.put("ALTER TABLE", "Modifies an existing table schema by adding, dropping, or modifying column definitions and constraints.\nSyntax:\n  ALTER TABLE tbl ADD column_name Type [constraints];\n  ALTER TABLE tbl DROP COLUMN column_name;\n  ALTER TABLE tbl MODIFY column_name NewType;\nExample: ALTER TABLE users ADD phone_number VARCHAR(20) UNIQUE;");
        HELP_MAP.put("TRUNCATE", "Instantly removes all data rows from a table while preserving table structure and resetting AUTO_INCREMENT counters.\nSyntax: TRUNCATE TABLE table_name;\nExample: TRUNCATE TABLE temporary_cache;");
        HELP_MAP.put("RENAME", "Renames an existing table to a new name.\nSyntax: RENAME TABLE old_table_name TO new_table_name;\nExample: RENAME TABLE app_user TO users;");
        HELP_MAP.put("DESCRIBE", "Displays detailed structural metadata for a table, including column names, data types, nullability, key indexes, default values, and extra attributes. Supports cross-database inspection.\nSyntax: DESCRIBE table_name; or DESCRIBE db_name.table_name;\nExample: DESCRIBE orders; DESCRIBE auth_db.users;");
        HELP_MAP.put("DESC", "Short alias for DESCRIBE. Displays schema metadata of a table.\nExample: DESC users;");
        HELP_MAP.put("USE", "Switches the active database context for subsequent SQL commands.\nSyntax: USE database_name;\nExample: USE e_commerce;");
        HELP_MAP.put("DELIMITER", "Changes the SQL statement terminator character. Essential when writing stored procedures or triggers containing internal semicolons.\nSyntax: DELIMITER //\nExample: DELIMITER //\nCREATE PROCEDURE testProc() BEGIN SELECT 1; END //\nDELIMITER ;");
        HELP_MAP.put("CONSTRAINT", "Defines a named data integrity constraint (PRIMARY KEY, FOREIGN KEY, UNIQUE, CHECK) during table creation or alteration.\nExample: CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)");
        HELP_MAP.put("COLUMN", "Keyword specifying column actions in ALTER TABLE statements.\nExample: ALTER TABLE users DROP COLUMN middle_name;");
        HELP_MAP.put("INDEX", "Creates an index on table columns to accelerate search and query performance.\nSyntax: CREATE INDEX index_name ON table_name (column_name);\nExample: CREATE INDEX idx_email ON users(email);");
        HELP_MAP.put("VIEW", "Creates a virtual table based on the result of a SELECT query.\nSyntax: CREATE VIEW view_name AS SELECT columns FROM table WHERE condition;\nExample: CREATE VIEW active_users AS SELECT id, name, email FROM users WHERE status = 'active';");
        HELP_MAP.put("CROSS-DATABASE QUERY", "Allows querying tables belonging to another database without switching the current active database context using dot notation.\nSyntax: SELECT * FROM database_name.table_name;\nExample: SELECT u.name, p.product_name FROM users u JOIN inventory_db.products p ON u.fav_product = p.id;");

        // Show
        HELP_MAP.put("SHOW", "Displays server metadata and status (databases, tables, columns, creation statements, etc.).");
        HELP_MAP.put("SHOW DATABASES", "Lists all available databases on the PocketSQL server storage.\nSyntax: SHOW DATABASES;");
        HELP_MAP.put("SHOW TABLES", "Lists all tables and views in the current active database or specified database.\nSyntax: SHOW TABLES; or SHOW TABLES FROM db_name;\nExample: SHOW TABLES FROM analytics_db;");
        HELP_MAP.put("SHOW COLUMNS", "Displays column metadata for a specified table.\nSyntax: SHOW COLUMNS FROM table_name [FROM db_name];\nExample: SHOW COLUMNS FROM users;");
        HELP_MAP.put("SHOW CREATE TABLE", "Displays the exact DDL CREATE TABLE statement used to construct a table schema.\nSyntax: SHOW CREATE TABLE table_name;\nExample: SHOW CREATE TABLE orders;");
        HELP_MAP.put("SHOW CREATE DATABASE", "Displays the CREATE DATABASE command for a database.\nSyntax: SHOW CREATE DATABASE db_name;");
        HELP_MAP.put("SHOW CREATE VIEW", "Displays the CREATE VIEW query definition.\nSyntax: SHOW CREATE VIEW view_name;");

        // Join
        HELP_MAP.put("JOIN", "Combines rows from two or more tables based on a related column between them.");
        HELP_MAP.put("INNER JOIN", "Returns records that have matching values in both left and right joined tables.\nSyntax: SELECT * FROM t1 INNER JOIN t2 ON t1.id = t2.t1_id;");
        HELP_MAP.put("LEFT JOIN", "Returns all records from the left table, and matching records from the right table. Non-matching right side yields NULL.\nSyntax: SELECT * FROM users u LEFT JOIN orders o ON u.id = o.user_id;");
        HELP_MAP.put("RIGHT JOIN", "Returns all records from the right table, and matching records from the left table.\nSyntax: SELECT * FROM orders o RIGHT JOIN users u ON o.user_id = u.id;");
        HELP_MAP.put("CROSS JOIN", "Produces a Cartesian product of all rows from joined tables (every row in t1 paired with every row in t2).\nSyntax: SELECT * FROM colors CROSS JOIN sizes;");
        HELP_MAP.put("ON", "Defines the join matching condition linking key columns between joined tables.\nExample: SELECT * FROM A JOIN B ON A.id = B.a_id;");

        // Clauses
        HELP_MAP.put("ORDER BY", "Sorts returned query rows in ascending (ASC) or descending (DESC) order based on one or more columns.\nSyntax: SELECT * FROM table ORDER BY column ASC|DESC;\nExample: SELECT * FROM products ORDER BY price DESC, name ASC;");
        HELP_MAP.put("GROUP BY", "Groups summary rows sharing identical column values together, typically used with aggregate functions (COUNT, SUM, AVG, MIN, MAX).\nSyntax: SELECT category, COUNT(*) FROM products GROUP BY category;");
        HELP_MAP.put("LIMIT", "Constrains the number of result rows returned by a query, with optional offset pagination.\nSyntax: SELECT * FROM table LIMIT count [OFFSET offset_val];\nExample: SELECT * FROM users ORDER BY id ASC LIMIT 10 OFFSET 20;");
        HELP_MAP.put("ASC", "Specifies ascending sort order (default) in ORDER BY clauses.\nExample: SELECT * FROM users ORDER BY created_at ASC;");
        HELP_MAP.put("HAVING", "Filters grouped summary rows after GROUP BY aggregations have been calculated.\nExample: SELECT category, AVG(price) FROM products GROUP BY category HAVING AVG(price) > 50.0;");
        HELP_MAP.put("AS", "Assigns a temporary alias name to a column or table for query readability.\nExample: SELECT u.first_name AS name, COUNT(*) AS total_orders FROM users AS u;");

        // Operators & Special
        HELP_MAP.put("AND", "Logical conjunction operator requiring all conditions to evaluate to TRUE.\nExample: SELECT * FROM users WHERE age >= 18 AND status = 'active';");
        HELP_MAP.put("OR", "Logical disjunction operator requiring at least one condition to evaluate to TRUE.\nExample: SELECT * FROM users WHERE role = 'admin' OR role = 'manager';");
        HELP_MAP.put("NOT", "Logical negation operator reversing a boolean condition.\nExample: SELECT * FROM users WHERE NOT (status = 'banned');");
        HELP_MAP.put("LIKE", "Searches for a specified pattern in a string column using wildcards ('%' matches multiple chars, '_' matches single char).\nExample: SELECT * FROM users WHERE email LIKE '%@gmail.com';");
        HELP_MAP.put("IN", "Filters values matching any item within a literal list or subquery result.\nExample: SELECT * FROM users WHERE country IN ('US', 'UK', 'CA');");
        HELP_MAP.put("BETWEEN", "Filters numeric, text, or date values within an inclusive range.\nExample: SELECT * FROM products WHERE price BETWEEN 10.0 AND 50.0;");
        HELP_MAP.put("IS NULL", "Evaluates whether a column value contains a NULL (missing) value.\nExample: SELECT * FROM users WHERE deleted_at IS NULL;");
        HELP_MAP.put("EXISTS", "Tests whether a subquery returns one or more matching rows.\nExample: SELECT * FROM users u WHERE EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id);");
        HELP_MAP.put("GLOBAL", "Sets system scope variables globally across all sessions.\nExample: SET GLOBAL autocommit = 1;");
        HELP_MAP.put("SESSION", "Sets system scope variables for current active connection session only.\nExample: SET SESSION sql_mode = 'ANSI';");

        // Constraints & Defaults & Attributes
        HELP_MAP.put("PRIMARY KEY", "Uniquely identifies each row record in a table. Implies UNIQUE and NOT NULL constraints.\nExample: CREATE TABLE users (id INT PRIMARY KEY, name TEXT);");
        HELP_MAP.put("AUTO_INCREMENT", "Automatically assigns sequential integer values (1, 2, 3...) to new inserted records.\nExample: id INT AUTO_INCREMENT PRIMARY KEY");
        HELP_MAP.put("UNIQUE", "Enforces that all values in a specified column or column combination remain distinct.\nExample: email VARCHAR(255) UNIQUE");
        HELP_MAP.put("DEFAULT", "Provides a fallback value for a column if no explicit value is specified during INSERT.\nExample: status VARCHAR(20) DEFAULT 'active'");
        HELP_MAP.put("FOREIGN KEY", "Enforces referential integrity between columns in child and parent tables.\nSyntax: FOREIGN KEY (child_col) REFERENCES parent_tbl(parent_col);\nExample: FOREIGN KEY (user_id) REFERENCES users(id)");
        HELP_MAP.put("REFERENCES", "Specifies parent table and target key column in a FOREIGN KEY constraint declaration.");
        HELP_MAP.put("CHECK", "Ensures values placed in a column satisfy a boolean expression condition.\nExample: CHECK (age >= 18 AND salary > 0)");
        HELP_MAP.put("NOT NULL", "Ensures that a column cannot contain NULL (empty) values.\nExample: username VARCHAR(50) NOT NULL");
        HELP_MAP.put("UNSIGNED", "Numeric attribute permitting only non-negative (zero or positive) integer values.\nExample: age INT UNSIGNED");
        HELP_MAP.put("ZEROFILL", "Pads numeric display output with leading zeros up to the declared column width.\nExample: code INT(5) ZEROFILL -- outputs 00042");
        HELP_MAP.put("ON UPDATE CURRENT_TIMESTAMP", "Automatically refreshes timestamp column value to current time whenever row is updated.\nExample: updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        HELP_MAP.put("CURRENT_TIMESTAMP", "System function resolving to current date and time value.");

        // User & Privilege & Security
        HELP_MAP.put("CREATE USER", "Creates a new database authentication user account.\nSyntax: CREATE USER 'username'@'host' IDENTIFIED BY 'password';\nExample: CREATE USER 'admin'@'localhost' IDENTIFIED BY 'Secret123!';");
        HELP_MAP.put("GRANT", "Grants specified database privileges to a target user account.\nSyntax: GRANT privilege ON db.* TO 'user'@'host';\nExample: GRANT ALL PRIVILEGES ON *.* TO 'admin'@'localhost';");
        HELP_MAP.put("REVOKE", "Removes specified privileges from a user account.\nSyntax: REVOKE privilege ON db.* FROM 'user'@'host';\nExample: REVOKE DELETE ON shop_db.* FROM 'clerk'@'localhost';");
        HELP_MAP.put("FLUSH PRIVILEGES", "Reloads access privilege grants from system grant tables into memory.\nSyntax: FLUSH PRIVILEGES;");
        HELP_MAP.put("EXPORT", "Dumps database structure and data records to local storage in .sql, .db (SQLCipher), .xlsx (Excel), or .csv formats.\nSyntax: EXPORT DATABASE db_name TO '/path/to/backup.ext';\nExample: EXPORT DATABASE shop_db TO '/sdcard/Download/backup.sql'; EXPORT DATABASE shop_db TO '/sdcard/Download/data.xlsx';");
        HELP_MAP.put("IMPORT", "Restores database structure and data records from a backup file (.sql, .db, .xlsx, .csv).\nSyntax: IMPORT DATABASE db_name FROM '/path/to/backup.ext';\nExample: IMPORT DATABASE shop_db FROM '/sdcard/Download/backup.sql';");
        HELP_MAP.put("ENCRYPTION", "Provides hardware-accelerated AES-256 / SQLCipher binary file encryption on SQLite storage, securing local database files at rest.");
        HELP_MAP.put("REST API", "Embedded PocketSQL HTTP Server listening on port 8080. Enables web & mobile apps to execute remote SQL queries via JSON REST API endpoints.");
        HELP_MAP.put("API KEYS", "Secures REST API endpoints with generated authentication tokens. Manage API keys via top toolbar or settings dialog.");
        HELP_MAP.put("CONNECTIONS", "Saved host & credential profiles for quick 1-tap switching and login authentication.");

        // Stored Procedures, Functions & Triggers
        HELP_MAP.put("CREATE PROCEDURE", "Constructs a reusable stored procedure block containing procedural SQL statements.\nSyntax: CREATE PROCEDURE procedure_name() BEGIN statement1; statement2; END;\nExample: CREATE PROCEDURE ResetStats() BEGIN UPDATE stats SET count = 0; END;");
        HELP_MAP.put("CALL", "Invokes and executes a previously created stored procedure.\nSyntax: CALL procedure_name();\nExample: CALL ResetStats();");
        HELP_MAP.put("DROP PROCEDURE", "Removes a stored procedure from active database context.\nSyntax: DROP PROCEDURE procedure_name;\nExample: DROP PROCEDURE ResetStats;");
        HELP_MAP.put("CREATE FUNCTION", "Constructs a scalar user-defined function returning a typed value.\nSyntax: CREATE FUNCTION func_name() RETURNS INT BEGIN RETURN 42; END;\nExample: CREATE FUNCTION GetTax(price DECIMAL(10,2)) RETURNS DECIMAL(10,2) BEGIN RETURN price * 0.18; END;");
        HELP_MAP.put("DROP FUNCTION", "Removes a user-defined function.\nSyntax: DROP FUNCTION function_name;\nExample: DROP FUNCTION GetTax;");
        HELP_MAP.put("CREATE TRIGGER", "Constructs an automated trigger invoked BEFORE or AFTER INSERT, UPDATE, or DELETE operations on a target table.\nSyntax: CREATE TRIGGER trigger_name BEFORE|AFTER INSERT|UPDATE|DELETE ON table_name FOR EACH ROW BEGIN ... END;\nExample: CREATE TRIGGER audit_log AFTER INSERT ON users FOR EACH ROW BEGIN INSERT INTO logs (msg) VALUES ('User added'); END;");
        HELP_MAP.put("DROP TRIGGER", "Deletes a database trigger.\nSyntax: DROP TRIGGER trigger_name;\nExample: DROP TRIGGER audit_log;");

        // Transaction
        HELP_MAP.put("START TRANSACTION", "Initiates an atomic transaction block. Subsequent modifications are isolated until COMMIT or ROLLBACK.\nSyntax: START TRANSACTION;");
        HELP_MAP.put("BEGIN", "Initiates an atomic transaction block (alias for START TRANSACTION).\nSyntax: BEGIN;");
        HELP_MAP.put("COMMIT", "Saves all modifications performed during current active transaction permanently to disk.\nSyntax: COMMIT;");
        HELP_MAP.put("ROLLBACK", "Undoes all modifications made during current transaction, restoring database to pre-transaction state.\nSyntax: ROLLBACK;");
        HELP_MAP.put("SAVEPOINT", "Establishes a named checkpoint within a transaction for partial rollbacks.\nSyntax: SAVEPOINT savepoint_name; ROLLBACK TO savepoint_name;\nExample: SAVEPOINT sp1; ROLLBACK TO sp1;");

        // Types
        HELP_MAP.put("INT", "4-byte signed integer numeric type (-2147483648 to 2147483647).\nExample: age INT");
        HELP_MAP.put("VARCHAR", "Variable-length character string type with maximum declared length.\nExample: name VARCHAR(255)");
        HELP_MAP.put("TEXT", "Large variable-length character string storage type for long documents.");
        HELP_MAP.put("DECIMAL", "Exact fixed-point numeric data type ideal for monetary calculations.\nSyntax: DECIMAL(precision, scale);\nExample: price DECIMAL(10,2) -- up to 99999999.99");
        HELP_MAP.put("DATETIME", "Date and time tracking format (YYYY-MM-DD HH:MM:SS).\nExample: created_at DATETIME");
        HELP_MAP.put("TIMESTAMP", "Date and time tracking format stored as UTC epoch seconds.\nExample: updated_at TIMESTAMP");

        // Functions - String & Utility
        HELP_MAP.put("CONCAT()", "Concatenates two or more string expressions together into a single string.\nSyntax: CONCAT(str1, str2, ...);\nExample: SELECT CONCAT(first_name, ' ', last_name) AS full_name FROM users;");
        HELP_MAP.put("UPPER()", "Converts all characters in a string expression to uppercase.\nSyntax: UPPER(string);\nExample: SELECT UPPER('pocketsql'); -- outputs 'POCKETSQL'");
        HELP_MAP.put("LOWER()", "Converts all characters in a string expression to lowercase.\nSyntax: LOWER(string);\nExample: SELECT LOWER('POCKETSQL'); -- outputs 'pocketsql'");
        HELP_MAP.put("SUBSTRING()", "Extracts a substring from a string starting at specified 1-based position for a given length.\nSyntax: SUBSTRING(string, start_pos, length);\nExample: SELECT SUBSTRING('PocketSQL', 1, 6); -- outputs 'Pocket'");
        HELP_MAP.put("LENGTH()", "Returns the character count length of a string expression.\nSyntax: LENGTH(string);\nExample: SELECT LENGTH('PocketSQL'); -- outputs 9");
        HELP_MAP.put("TRIM()", "Strips leading and trailing spaces from a string value.\nSyntax: TRIM(string);\nExample: SELECT TRIM('  hello world  '); -- outputs 'hello world'");
        HELP_MAP.put("REPLACE()", "Replaces all occurrences of a specified substring within a string with a new substring.\nSyntax: REPLACE(str, old_sub, new_sub);\nExample: SELECT REPLACE('hello world', 'world', 'PocketSQL'); -- outputs 'hello PocketSQL'");

        // CTE & Window Functions
        HELP_MAP.put("WITH", "Common Table Expression (CTE) defining a temporary named result set for query reference.\nSyntax: WITH cte_name AS (SELECT ...) SELECT * FROM cte_name;\nExample: WITH HighSales AS (SELECT user_id, SUM(total) AS rev FROM orders GROUP BY user_id) SELECT * FROM HighSales WHERE rev > 1000;");
        HELP_MAP.put("CTE", "Alias for Common Table Expression defined using WITH clause.\nExample: WITH RankedData AS (SELECT id, DENSE_RANK() OVER (ORDER BY score DESC) AS `rank` FROM test) SELECT * FROM RankedData WHERE `rank` <= 3;");
        HELP_MAP.put("OVER()", "Specifies window partitioning and ordering for analytical window functions (DENSE_RANK, ROW_NUMBER, RANK).\nSyntax: function() OVER ([PARTITION BY col] [ORDER BY col ASC|DESC]);\nExample: SELECT name, DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS `rank` FROM emp;");
        HELP_MAP.put("DENSE_RANK()", "Window function returning consecutive rank numbers without gaps for tied values.\nSyntax: DENSE_RANK() OVER ([PARTITION BY col] ORDER BY col ASC|DESC);\nExample: SELECT name, salary, DENSE_RANK() OVER (PARTITION BY category ORDER BY salary DESC) AS `rank` FROM products;");
        HELP_MAP.put("ROW_NUMBER()", "Window function returning sequential 1-based row index within partition.\nSyntax: ROW_NUMBER() OVER ([PARTITION BY col] ORDER BY col ASC|DESC);");
        HELP_MAP.put("RANK()", "Window function returning rank numbers with gaps for tied scores.\nSyntax: RANK() OVER ([PARTITION BY col] ORDER BY col ASC|DESC);");
        HELP_MAP.put("WITH ROLLUP", "Extension of GROUP BY to produce summary super-aggregate total rows.\nSyntax: SELECT category, SUM(sales) FROM products GROUP BY category WITH ROLLUP;");
        HELP_MAP.put("DROP TABLE IF EXISTS", "Deletes a table safely without error if the table does not exist.\nSyntax: DROP TABLE IF EXISTS table_name;");

        // Functions - Date & Time
        HELP_MAP.put("NOW()", "Returns the current local system date and time.\nSyntax: NOW();\nExample: SELECT NOW(); -- outputs '2026-08-24 20:30:00'");
        HELP_MAP.put("CURDATE()", "Returns the current local system date.\nSyntax: CURDATE();\nExample: SELECT CURDATE(); -- outputs '2026-08-24'");
        HELP_MAP.put("CURTIME()", "Returns the current local system time.\nSyntax: CURTIME();\nExample: SELECT CURTIME(); -- outputs '20:30:00'");
        HELP_MAP.put("DATE_FORMAT()", "Formats a date/time value according to a format string pattern.\nSyntax: DATE_FORMAT(date_expr, format_pattern);\nExample: SELECT DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s');");
        HELP_MAP.put("DATE_ADD()", "Adds a specified time unit interval to a date value.\nSyntax: DATE_ADD(date, INTERVAL expr unit);\nExample: SELECT DATE_ADD(NOW(), INTERVAL 7 DAY);");
        HELP_MAP.put("DATEDIFF()", "Calculates difference in days between two date expressions.\nSyntax: DATEDIFF(date1, date2);\nExample: SELECT DATEDIFF('2026-08-31', '2026-08-24'); -- outputs 7");
        HELP_MAP.put("MONTHNAME()", "Returns full month name of a date string or timestamp.\nSyntax: MONTHNAME(date);\nExample: SELECT MONTHNAME('2026-08-31'); -- outputs 'August'");
        HELP_MAP.put("YEAR()", "Extracts 4-digit year integer from a date value.\nSyntax: YEAR(date);\nExample: SELECT YEAR('2026-08-31'); -- outputs 2026");
        HELP_MAP.put("MONTH()", "Extracts month integer (1-12) from a date value.\nSyntax: MONTH(date);\nExample: SELECT MONTH('2026-08-31'); -- outputs 8");
        HELP_MAP.put("DAY()", "Extracts day of month integer (1-31) from a date value.\nSyntax: DAY(date);\nExample: SELECT DAY('2026-08-31'); -- outputs 31");

        // Functions - Mathematical & Aggregate
        HELP_MAP.put("ABS()", "Returns absolute non-negative value of a number.\nSyntax: ABS(number);\nExample: SELECT ABS(-42); -- outputs 42");
        HELP_MAP.put("ROUND()", "Rounds a numeric value to specified decimal places.\nSyntax: ROUND(number, decimals);\nExample: SELECT ROUND(3.14159, 2); -- outputs 3.14");
        HELP_MAP.put("CEIL()", "Returns smallest integer value greater than or equal to a number.\nSyntax: CEIL(number);\nExample: SELECT CEIL(4.2); -- outputs 5");
        HELP_MAP.put("FLOOR()", "Returns largest integer value less than or equal to a number.\nSyntax: FLOOR(number);\nExample: SELECT FLOOR(4.8); -- outputs 4");
        HELP_MAP.put("POW()", "Calculates base raised to power exponent.\nSyntax: POW(base, exponent);\nExample: SELECT POW(2, 8); -- outputs 256");
        HELP_MAP.put("SQRT()", "Calculates non-negative square root of a number.\nSyntax: SQRT(number);\nExample: SELECT SQRT(64); -- outputs 8");
        HELP_MAP.put("MOD()", "Returns remainder of division operation (N % M).\nSyntax: MOD(N, M);\nExample: SELECT MOD(10, 3); -- outputs 1");
        HELP_MAP.put("RAND()", "Returns pseudo-random floating point number between 0.0 and 1.0.\nSyntax: RAND();\nExample: SELECT RAND();");
        HELP_MAP.put("IF()", "Conditional expression returning first value if condition is TRUE, second if FALSE.\nSyntax: IF(condition, true_value, false_value);\nExample: SELECT IF(score >= 50, 'Pass', 'Fail') FROM exams;");
        HELP_MAP.put("IFNULL()", "Returns alternative value if primary expression evaluates to NULL.\nSyntax: IFNULL(val, alt_val);\nExample: SELECT IFNULL(phone, 'N/A') FROM users;");
        HELP_MAP.put("COALESCE()", "Returns the first non-NULL expression from a list of arguments.\nSyntax: COALESCE(v1, v2, ...);\nExample: SELECT COALESCE(mobile, phone, email, 'No Contact') FROM users;");
        HELP_MAP.put("COUNT()", "Aggregate function returning count of matching rows or non-NULL column values.\nSyntax: COUNT(*) or COUNT(column);\nExample: SELECT COUNT(*) FROM users WHERE status = 'active';");
        HELP_MAP.put("SUM()", "Aggregate function calculating total sum of numeric column values.\nSyntax: SUM(column);\nExample: SELECT SUM(amount) FROM transactions;");
        HELP_MAP.put("AVG()", "Aggregate function calculating average mean of numeric column values.\nSyntax: AVG(column);\nExample: SELECT AVG(price) FROM products;");
        HELP_MAP.put("MIN()", "Aggregate function returning minimum column value.\nSyntax: MIN(column);\nExample: SELECT MIN(price) FROM products;");
        HELP_MAP.put("MAX()", "Aggregate function returning maximum column value.\nSyntax: MAX(column);\nExample: SELECT MAX(price) FROM products;");

        // Functions - JSON
        HELP_MAP.put("JSON_EXTRACT()", "Extracts data value from a JSON formatted column or string at specified JSON path.\nSyntax: JSON_EXTRACT(json_doc, path);\nExample: SELECT JSON_EXTRACT(metadata, '$.user.name') FROM logs;");
        HELP_MAP.put("JSON_OBJECT()", "Constructs a JSON object from key-value pairs.\nSyntax: JSON_OBJECT(key1, val1, key2, val2...);\nExample: SELECT JSON_OBJECT('id', 1, 'name', 'PocketSQL');");
        HELP_MAP.put("JSON_ARRAY()", "Constructs a JSON array from element arguments.\nSyntax: JSON_ARRAY(val1, val2...);\nExample: SELECT JSON_ARRAY(10, 20, 30);");

        // UI & Tools
        HELP_MAP.put("TEMPLATES", "Pre-built SQL code snippets (DDL, DML, Joins, Functions, Triggers, Views) accessible via top toolbar button or template picker dialog.");
        HELP_MAP.put("SHORTCUTS", "Special character bar (; , ( ) ' \" = - + * / _ % @ . !) and history navigation keys (UP/DOWN buttons) for rapid mobile SQL query entry.");

        // ── DML Modifiers ───────────────────────────────────────────────────────
        HELP_MAP.put("IGNORE", "Suppresses duplicate key / constraint errors during INSERT or UPDATE — skips offending rows instead of failing.\nExample: INSERT IGNORE INTO users (email) VALUES ('dup@test.com');");
        HELP_MAP.put("ALL", "Used with UNION ALL to keep duplicate rows, or implies SELECT ALL (default).\nExample: SELECT * FROM t1 UNION ALL SELECT * FROM t2;");
        HELP_MAP.put("RECURSIVE", "Used with WITH to create recursive CTEs for hierarchical/tree queries.\nSyntax: WITH RECURSIVE cte AS (base UNION ALL recursive) SELECT * FROM cte;\nExample: WITH RECURSIVE nums AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM nums WHERE n<5) SELECT * FROM nums;");
        HELP_MAP.put("DUPLICATE", "See ON DUPLICATE KEY UPDATE.");
        HELP_MAP.put("ON DUPLICATE KEY UPDATE", "Executes UPDATE assignments when INSERT violates a unique or primary key constraint.\nSyntax: INSERT INTO t (col) VALUES (val) ON DUPLICATE KEY UPDATE col = val;\nExample: INSERT INTO visits (page, count) VALUES ('home',1) ON DUPLICATE KEY UPDATE count = count+1;");

        // ── DDL modifiers ────────────────────────────────────────────────────────
        HELP_MAP.put("ENGINE", "Specifies the storage engine for a table (InnoDB, MyISAM, etc.).\nExample: CREATE TABLE orders (...) ENGINE=InnoDB;");
        HELP_MAP.put("CHARSET", "Sets the character set encoding for a table or column.\nExample: CREATE TABLE msgs (body TEXT) CHARSET=utf8mb4;");
        HELP_MAP.put("FULLTEXT", "Creates a FULLTEXT index for fast full-text searching.\nExample: CREATE FULLTEXT INDEX idx_body ON articles(body);");
        HELP_MAP.put("SPATIAL", "Creates a SPATIAL index on geometry columns.\nExample: CREATE SPATIAL INDEX idx_loc ON places(location);");
        HELP_MAP.put("CHARACTER SET", "Sets the character encoding for a column or table.\nExample: name VARCHAR(100) CHARACTER SET utf8mb4;");
        HELP_MAP.put("PRAGMA", "SQLite-specific command for querying or setting engine settings.\nExample: PRAGMA table_info(users); PRAGMA foreign_keys = ON;");
        HELP_MAP.put("ADD", "Used in ALTER TABLE to add a new column or constraint.\nExample: ALTER TABLE users ADD COLUMN age INT;");
        HELP_MAP.put("MODIFY", "Used in ALTER TABLE to change a column's type or attributes.\nExample: ALTER TABLE users MODIFY COLUMN age SMALLINT UNSIGNED;");
        HELP_MAP.put("CHANGE", "Used in ALTER TABLE to rename a column and/or change its type.\nExample: ALTER TABLE users CHANGE old_name new_name VARCHAR(100);");
        HELP_MAP.put("FIRST", "Positions a new/modified column as the first column.\nExample: ALTER TABLE t ADD COLUMN id INT FIRST;");
        HELP_MAP.put("AFTER", "Positions a new/modified column after a specified column.\nExample: ALTER TABLE t ADD COLUMN phone VARCHAR(20) AFTER email;");
        HELP_MAP.put("COLLATE", "Sets the collation (sort/compare rules) for a string column.\nExample: name VARCHAR(100) COLLATE utf8mb4_unicode_ci;");
        HELP_MAP.put("COMMENT", "Attaches a descriptive comment to a column or table definition.\nExample: age INT COMMENT 'User age in years';");
        HELP_MAP.put("CASCADE", "Foreign key action: propagates DELETE/UPDATE from parent to child rows.\nExample: FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;");
        HELP_MAP.put("RESTRICT", "Foreign key action: prevents DELETE/UPDATE on parent if child rows reference it.\nExample: ON DELETE RESTRICT");
        HELP_MAP.put("NO ACTION", "Foreign key action: same as RESTRICT.\nExample: ON DELETE NO ACTION");
        HELP_MAP.put("SET NULL", "Foreign key action: sets child column to NULL when parent row is deleted.\nExample: ON DELETE SET NULL");
        HELP_MAP.put("VISIBLE", "Makes an index visible to the query optimizer (default).\nExample: ALTER TABLE t ALTER INDEX idx_email VISIBLE;");
        HELP_MAP.put("INVISIBLE", "Hides an index from the optimizer without dropping it.\nExample: ALTER TABLE t ALTER INDEX idx_email INVISIBLE;");
        HELP_MAP.put("VIRTUAL", "Generated column computed on-the-fly, not stored on disk.\nExample: full_name VARCHAR(200) GENERATED ALWAYS AS (CONCAT(first,' ',last)) VIRTUAL;");
        HELP_MAP.put("STORED", "Generated column computed from expression and physically stored on disk.\nExample: total DECIMAL(10,2) GENERATED ALWAYS AS (qty * price) STORED;");
        HELP_MAP.put("ALWAYS", "Keyword in GENERATED column syntax.\nExample: col INT GENERATED ALWAYS AS (expr) STORED;");
        HELP_MAP.put("GENERATED", "Declares a column whose value is automatically computed.\nSyntax: col type GENERATED ALWAYS AS (expr) [VIRTUAL|STORED];\nExample: total DECIMAL(10,2) GENERATED ALWAYS AS (quantity * unit_price) STORED;");
        HELP_MAP.put("ON UPDATE", "Specifies a foreign key action or timestamp auto-update on row UPDATE.\nExample: updated_at TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;");
        HELP_MAP.put("ON UPDATE CASCADE", "Updates child FK values automatically when the parent key changes.\nExample: FOREIGN KEY (dept_id) REFERENCES depts(id) ON UPDATE CASCADE;");

        // ── SHOW extras ──────────────────────────────────────────────────────────
        HELP_MAP.put("SHOW STATUS", "Shows server status variables.\nSyntax: SHOW STATUS;");
        HELP_MAP.put("SHOW PROCESSLIST", "Displays currently running queries.\nSyntax: SHOW PROCESSLIST;");
        HELP_MAP.put("SHOW ERRORS", "Lists recent error messages.\nSyntax: SHOW ERRORS;");
        HELP_MAP.put("SHOW WARNINGS", "Lists warnings from the last statement.\nSyntax: SHOW WARNINGS;");
        HELP_MAP.put("SHOW VARIABLES", "Displays server system variables.\nSyntax: SHOW VARIABLES; or SHOW VARIABLES LIKE 'pattern%';");
        HELP_MAP.put("SHOW SCHEMAS", "Alias for SHOW DATABASES.\nSyntax: SHOW SCHEMAS;");
        HELP_MAP.put("SHOW GRANTS", "Displays privilege grants for a user.\nSyntax: SHOW GRANTS FOR 'user'@'host';");
        HELP_MAP.put("SHOW ENGINES", "Lists available storage engines.\nSyntax: SHOW ENGINES;");
        HELP_MAP.put("SHOW PLUGINS", "Lists installed server plugins.\nSyntax: SHOW PLUGINS;");
        HELP_MAP.put("SHOW TRIGGERS", "Lists triggers in the current database.\nSyntax: SHOW TRIGGERS;");
        HELP_MAP.put("SHOW EVENTS", "Lists scheduled events.\nSyntax: SHOW EVENTS;");

        // ── JOIN extras ──────────────────────────────────────────────────────────
        HELP_MAP.put("NATURAL JOIN", "Joins tables on all identically-named columns, eliminating duplicate columns.\nExample: SELECT * FROM employees NATURAL JOIN departments;");
        HELP_MAP.put("FULL OUTER JOIN", "Returns all rows from both tables — non-matching rows appear with NULLs.\nExample: SELECT * FROM a LEFT JOIN b ON a.id=b.id UNION SELECT * FROM a RIGHT JOIN b ON a.id=b.id;");
        HELP_MAP.put("USING", "Shorthand join condition for identically-named columns.\nExample: SELECT * FROM orders JOIN customers USING (customer_id);");
        HELP_MAP.put("INNER", "Qualifies INNER JOIN — only rows with matches in both tables.");
        HELP_MAP.put("LEFT", "Qualifies LEFT JOIN — all rows from left table, NULLs for unmatched right rows.");
        HELP_MAP.put("RIGHT", "Qualifies RIGHT JOIN — all rows from right table, NULLs for unmatched left rows.");
        HELP_MAP.put("CROSS", "Qualifies CROSS JOIN — Cartesian product of both tables.");
        HELP_MAP.put("FULL", "Qualifies FULL OUTER JOIN.");
        HELP_MAP.put("OUTER", "Used with LEFT/RIGHT/FULL JOIN to include non-matching rows.");
        HELP_MAP.put("NATURAL", "Used with NATURAL JOIN.");

        // ── Clause extras ────────────────────────────────────────────────────────
        HELP_MAP.put("OFFSET", "Skips N rows before returning results (pagination with LIMIT).\nSyntax: SELECT * FROM t LIMIT 10 OFFSET 20;\nExample: SELECT * FROM users ORDER BY id LIMIT 10 OFFSET 30; -- page 4");
        HELP_MAP.put("BY", "Used with ORDER BY, GROUP BY — specifies sort/group columns.");
        HELP_MAP.put("ROLLUP", "Adds super-aggregate summary rows for each GROUP BY level.\nExample: SELECT dept, SUM(salary) FROM emp GROUP BY dept WITH ROLLUP;");
        HELP_MAP.put("OVER", "Defines the window frame for a window function.\nSyntax: function() OVER ([PARTITION BY col] [ORDER BY col]);\nExample: RANK() OVER (PARTITION BY dept ORDER BY salary DESC)");
        HELP_MAP.put("PARTITION", "Used inside OVER() to divide rows into groups for window functions.\nExample: DENSE_RANK() OVER (PARTITION BY category ORDER BY sales DESC)");

        // ── Operator extras ──────────────────────────────────────────────────────
        HELP_MAP.put("NOT LIKE", "Filters rows where column does NOT match a LIKE pattern.\nExample: SELECT * FROM users WHERE email NOT LIKE '%@spam.com';");
        HELP_MAP.put("REGEXP", "Filters rows matching a regular expression.\nExample: SELECT * FROM users WHERE name REGEXP '^[A-Z]';");
        HELP_MAP.put("RLIKE", "Alias for REGEXP.\nExample: SELECT * FROM products WHERE code RLIKE '[0-9]{4}';");
        HELP_MAP.put("NOT IN", "Filters rows whose value does NOT appear in a list.\nExample: SELECT * FROM orders WHERE status NOT IN ('cancelled','refunded');");
        HELP_MAP.put("NOT BETWEEN", "Filters rows outside a specified inclusive range.\nExample: SELECT * FROM products WHERE price NOT BETWEEN 10 AND 50;");
        HELP_MAP.put("IS NOT NULL", "Filters rows where column contains a non-NULL value.\nExample: SELECT * FROM users WHERE phone IS NOT NULL;");
        HELP_MAP.put("IS", "Comparison for IS NULL, IS NOT NULL, IS TRUE, IS FALSE.\nExample: SELECT * FROM tasks WHERE completed IS NULL;");
        HELP_MAP.put("NULL", "Represents a missing or unknown value. Use IS NULL / IS NOT NULL for comparisons.");
        HELP_MAP.put("CASE", "Conditional expression returning matched THEN value.\nSyntax: CASE WHEN cond1 THEN v1 WHEN cond2 THEN v2 ELSE default END;\nExample: SELECT CASE WHEN score>=90 THEN 'A' WHEN score>=75 THEN 'B' ELSE 'C' END FROM results;");
        HELP_MAP.put("WHEN", "Defines a condition branch inside a CASE expression.\nExample: CASE WHEN price > 100 THEN 'Expensive' ... END");
        HELP_MAP.put("THEN", "Value returned when a WHEN condition is TRUE.\nExample: WHEN status='active' THEN 1");
        HELP_MAP.put("ELSE", "Fallback value in a CASE when no WHEN condition matches.\nExample: CASE WHEN x>0 THEN 'Positive' ELSE 'Non-positive' END");
        HELP_MAP.put("END", "Closes a CASE expression block.\nExample: CASE WHEN ... THEN ... ELSE ... END");
        HELP_MAP.put("NAMES", "Sets connection character set.\nSyntax: SET NAMES 'utf8mb4';");
        HELP_MAP.put("NOT EXISTS", "TRUE if subquery returns no rows.\nExample: SELECT * FROM p WHERE NOT EXISTS (SELECT 1 FROM oi WHERE oi.product_id = p.id);");
        HELP_MAP.put("IF", "Conditional: IF(condition, true_val, false_val).\nExample: SELECT IF(score >= 50, 'Pass', 'Fail') FROM exams;");

        // ── Data Types: Numeric ──────────────────────────────────────────────────
        HELP_MAP.put("TINYINT", "1-byte integer (-128 to 127, or 0-255 UNSIGNED). Often used for boolean flags.\nExample: is_active TINYINT(1) DEFAULT 1;");
        HELP_MAP.put("SMALLINT", "2-byte integer (-32768 to 32767, or 0-65535 UNSIGNED).\nExample: age SMALLINT UNSIGNED;");
        HELP_MAP.put("MEDIUMINT", "3-byte integer (-8388608 to 8388607).\nExample: population MEDIUMINT UNSIGNED;");
        HELP_MAP.put("INTEGER", "Alias for INT — 4-byte signed integer.\nExample: user_id INTEGER PRIMARY KEY;");
        HELP_MAP.put("BIGINT", "8-byte integer for very large numbers.\nExample: file_size BIGINT UNSIGNED;");
        HELP_MAP.put("BIT", "Stores bit-field values (1 to 64 bits).\nExample: permissions BIT(8);");
        HELP_MAP.put("YEAR", "Stores a 4-digit year (1901-2155).\nExample: graduation_year YEAR;");
        HELP_MAP.put("FLOAT", "4-byte approximate floating-point.\nExample: temperature FLOAT;");
        HELP_MAP.put("DOUBLE", "8-byte double-precision floating-point.\nExample: latitude DOUBLE;");
        HELP_MAP.put("REAL", "Alias for DOUBLE.\nExample: ratio REAL;");
        HELP_MAP.put("NUMERIC", "Alias for DECIMAL.\nExample: balance NUMERIC(15,4);");
        HELP_MAP.put("SIGNED", "Allows both negative and positive integer values (default).\nExample: temperature INT SIGNED;");

        // ── Data Types: String ───────────────────────────────────────────────────
        HELP_MAP.put("CHAR", "Fixed-length character string (padded with spaces).\nSyntax: CHAR(length);\nExample: country_code CHAR(2);");
        HELP_MAP.put("TINYTEXT", "Very short text string (max 255 bytes).\nExample: status_msg TINYTEXT;");
        HELP_MAP.put("MEDIUMTEXT", "Medium-size text storage (max ~16 MB).\nExample: article_body MEDIUMTEXT;");
        HELP_MAP.put("LONGTEXT", "Maximum-size text storage (max ~4 GB).\nExample: raw_log LONGTEXT;");
        HELP_MAP.put("BINARY", "Fixed-length binary byte string.\nExample: hash BINARY(16);");
        HELP_MAP.put("VARBINARY", "Variable-length binary byte string.\nExample: token VARBINARY(64);");
        HELP_MAP.put("BLOB", "Binary Large Object (max 65535 bytes).\nExample: profile_image BLOB;");
        HELP_MAP.put("TINYBLOB", "Small binary storage (max 255 bytes).\nExample: icon TINYBLOB;");
        HELP_MAP.put("MEDIUMBLOB", "Medium binary storage (max ~16 MB).\nExample: document MEDIUMBLOB;");
        HELP_MAP.put("LONGBLOB", "Maximum binary storage (max ~4 GB).\nExample: video_file LONGBLOB;");
        HELP_MAP.put("JSON", "Native JSON document storage with validation and path-based extraction.\nExample: metadata JSON;");
        HELP_MAP.put("ENUM", "String column restricted to a predefined set of values.\nSyntax: ENUM('val1','val2',...);\nExample: status ENUM('active','inactive','banned') DEFAULT 'active';");

        // ── Data Types: Geometry ─────────────────────────────────────────────────
        HELP_MAP.put("GEOMETRY", "Base geometry type for any shape.\nExample: shape GEOMETRY;");
        HELP_MAP.put("POINT", "2D coordinate location (x, y).\nExample: location POINT;");
        HELP_MAP.put("LINESTRING", "Sequence of connected points forming a line.\nExample: path LINESTRING;");
        HELP_MAP.put("POLYGON", "Closed polygon shape.\nExample: area POLYGON;");
        HELP_MAP.put("MULTIPOINT", "Collection of POINT geometries.\nExample: stops MULTIPOINT;");
        HELP_MAP.put("MULTILINESTRING", "Collection of LINESTRING geometries.\nExample: routes MULTILINESTRING;");
        HELP_MAP.put("MULTIPOLYGON", "Collection of POLYGON geometries.\nExample: zones MULTIPOLYGON;");
        HELP_MAP.put("GEOMETRYCOLLECTION", "Collection of mixed geometry types.\nExample: shapes GEOMETRYCOLLECTION;");

        // ── Data Types: Date & Time ──────────────────────────────────────────────
        HELP_MAP.put("DATE", "Stores date only (YYYY-MM-DD).\nExample: birth_date DATE;");
        HELP_MAP.put("TIME", "Stores time of day (HH:MM:SS).\nExample: shift_start TIME;");

        // ── Stored Program Control Flow ──────────────────────────────────────────
        HELP_MAP.put("RETURNS", "Specifies the return type of a user-defined FUNCTION.\nExample: CREATE FUNCTION GetAge() RETURNS INT BEGIN ... END;");
        HELP_MAP.put("RETURN", "Exits a function and returns a value.\nExample: RETURN price * 0.18;");
        HELP_MAP.put("DECLARE", "Declares local variables inside a stored procedure or function.\nExample: DECLARE total INT DEFAULT 0;");
        HELP_MAP.put("LOOP", "Infinite loop in stored program — exits via LEAVE.\nSyntax: label: LOOP ... LEAVE label; END LOOP;");
        HELP_MAP.put("WHILE", "Repeats a block while a condition is TRUE.\nSyntax: WHILE condition DO ... END WHILE;");
        HELP_MAP.put("REPEAT", "Executes a block and repeats UNTIL condition is TRUE.\nSyntax: REPEAT ... UNTIL condition END REPEAT;");
        HELP_MAP.put("UNTIL", "Termination condition for REPEAT loop.\nExample: REPEAT SET i=i+1; UNTIL i>=10 END REPEAT;");
        HELP_MAP.put("LEAVE", "Exits a labelled loop or BEGIN...END block.\nExample: myloop: LOOP ... LEAVE myloop; END LOOP;");

        // ── Functions: String extras ─────────────────────────────────────────────
        HELP_MAP.put("CONCAT_WS()", "Concatenates strings with a separator between each value.\nSyntax: CONCAT_WS(sep, str1, str2, ...);\nExample: SELECT CONCAT_WS(', ', city, state, country) AS address FROM users;");
        HELP_MAP.put("FORMAT()", "Formats a number with thousands separator and fixed decimals.\nSyntax: FORMAT(number, decimals);\nExample: SELECT FORMAT(1234567.891, 2); -- '1,234,567.89'");
        HELP_MAP.put("CHAR_LENGTH()", "Returns character count (not bytes) — important for multi-byte charsets.\nSyntax: CHAR_LENGTH(string);\nExample: SELECT CHAR_LENGTH('héllo'); -- 5");
        HELP_MAP.put("SUBSTR()", "Alias for SUBSTRING().\nSyntax: SUBSTR(str, start [, length]);\nExample: SELECT SUBSTR('PocketSQL', 7); -- 'SQL'");
        HELP_MAP.put("LEFT()", "Returns leftmost N characters.\nSyntax: LEFT(string, n);\nExample: SELECT LEFT('PocketSQL', 6); -- 'Pocket'");
        HELP_MAP.put("RIGHT()", "Returns rightmost N characters.\nSyntax: RIGHT(string, n);\nExample: SELECT RIGHT('PocketSQL', 3); -- 'SQL'");
        HELP_MAP.put("LTRIM()", "Removes leading whitespace.\nSyntax: LTRIM(string);\nExample: SELECT LTRIM('   hello'); -- 'hello'");
        HELP_MAP.put("RTRIM()", "Removes trailing whitespace.\nSyntax: RTRIM(string);\nExample: SELECT RTRIM('hello   '); -- 'hello'");
        HELP_MAP.put("INSTR()", "Returns 1-based position of first occurrence of substring (0 if not found).\nSyntax: INSTR(string, substring);\nExample: SELECT INSTR('PocketSQL', 'SQL'); -- 7");
        HELP_MAP.put("LOCATE()", "Returns position of substring in string, with optional start.\nSyntax: LOCATE(substr, str [, start]);\nExample: SELECT LOCATE('SQL', 'PocketSQL'); -- 7");
        HELP_MAP.put("LPAD()", "Left-pads string to specified length.\nSyntax: LPAD(string, length, pad);\nExample: SELECT LPAD('42', 5, '0'); -- '00042'");
        HELP_MAP.put("RPAD()", "Right-pads string to specified length.\nSyntax: RPAD(string, length, pad);\nExample: SELECT RPAD('42', 5, '*'); -- '42***'");
        HELP_MAP.put("REPEAT()", "Repeats a string N times.\nSyntax: REPEAT(string, count);\nExample: SELECT REPEAT('ab', 3); -- 'ababab'");
        HELP_MAP.put("HEX()", "Returns hexadecimal representation of string or integer.\nSyntax: HEX(val);\nExample: SELECT HEX(255); -- 'FF'");
        HELP_MAP.put("UNHEX()", "Converts hex string to binary representation.\nSyntax: UNHEX(hex_str);\nExample: SELECT UNHEX('FF');");
        HELP_MAP.put("FIELD()", "Returns 1-based position of value in a list (0 if not found).\nSyntax: FIELD(val, v1, v2, ...);\nExample: SELECT FIELD('b', 'a', 'b', 'c'); -- 2");
        HELP_MAP.put("FIND_IN_SET()", "Returns position of value in a comma-delimited string.\nSyntax: FIND_IN_SET(val, set_string);\nExample: SELECT FIND_IN_SET('b', 'a,b,c'); -- 2");
        HELP_MAP.put("ELT()", "Returns the Nth element from string arguments.\nSyntax: ELT(n, s1, s2, ...);\nExample: SELECT ELT(2, 'apple', 'banana', 'cherry'); -- 'banana'");
        HELP_MAP.put("MAKE_SET()", "Returns comma-separated strings where the corresponding bit is set.\nSyntax: MAKE_SET(bits, str1, str2, ...);\nExample: SELECT MAKE_SET(5, 'a', 'b', 'c'); -- 'a,c'");
        HELP_MAP.put("QUOTE()", "Returns a safely SQL-quoted string.\nSyntax: QUOTE(string);");
        HELP_MAP.put("ASCII()", "Returns ASCII code of first character.\nSyntax: ASCII(string);\nExample: SELECT ASCII('A'); -- 65");
        HELP_MAP.put("CHAR()", "Returns character(s) from ASCII code values.\nSyntax: CHAR(code1, code2, ...);\nExample: SELECT CHAR(65, 66, 67); -- 'ABC'");
        HELP_MAP.put("REVERSE()", "Returns string with characters reversed.\nSyntax: REVERSE(string);\nExample: SELECT REVERSE('SQL'); -- 'LQS'");

        // ── Functions: Numeric extras ────────────────────────────────────────────
        HELP_MAP.put("POWER()", "Alias for POW() — base raised to exponent.\nSyntax: POWER(base, exp);\nExample: SELECT POWER(2, 10); -- 1024");
        HELP_MAP.put("SIGN()", "Returns -1, 0, or 1 for negative, zero, positive.\nSyntax: SIGN(number);\nExample: SELECT SIGN(-5); -- -1");
        HELP_MAP.put("PI()", "Returns π (3.141593...).\nExample: SELECT PI();");
        HELP_MAP.put("EXP()", "Returns e^x.\nSyntax: EXP(x);\nExample: SELECT EXP(1); -- 2.718282");
        HELP_MAP.put("LOG()", "Returns natural logarithm (base e).\nSyntax: LOG(x) or LOG(base, x);\nExample: SELECT LOG(2.71828);");
        HELP_MAP.put("LOG10()", "Returns base-10 logarithm.\nSyntax: LOG10(x);\nExample: SELECT LOG10(1000); -- 3");
        HELP_MAP.put("LOG2()", "Returns base-2 logarithm.\nSyntax: LOG2(x);\nExample: SELECT LOG2(8); -- 3");
        HELP_MAP.put("DEGREES()", "Converts radians to degrees.\nSyntax: DEGREES(rad);\nExample: SELECT DEGREES(PI()); -- 180");
        HELP_MAP.put("RADIANS()", "Converts degrees to radians.\nSyntax: RADIANS(deg);\nExample: SELECT RADIANS(180);");
        HELP_MAP.put("SIN()", "Returns sine of angle in radians.\nSyntax: SIN(x);\nExample: SELECT SIN(PI()/2); -- 1");
        HELP_MAP.put("COS()", "Returns cosine of angle in radians.\nSyntax: COS(x);\nExample: SELECT COS(0); -- 1");
        HELP_MAP.put("TAN()", "Returns tangent of angle in radians.\nSyntax: TAN(x);\nExample: SELECT TAN(PI()/4); -- 1");
        HELP_MAP.put("ASIN()", "Returns arc sine in radians.\nSyntax: ASIN(x);\nExample: SELECT ASIN(1);");
        HELP_MAP.put("ACOS()", "Returns arc cosine in radians.\nSyntax: ACOS(x);\nExample: SELECT ACOS(1); -- 0");
        HELP_MAP.put("ATAN()", "Returns arc tangent in radians.\nSyntax: ATAN(x) or ATAN(y, x);\nExample: SELECT ATAN(1);");
        HELP_MAP.put("TRUNCATE()", "Truncates number to specified decimal places (no rounding).\nSyntax: TRUNCATE(number, decimals);\nExample: SELECT TRUNCATE(3.14159, 2); -- 3.14");
        HELP_MAP.put("LEAST()", "Returns the smallest value from arguments.\nSyntax: LEAST(v1, v2, ...);\nExample: SELECT LEAST(10, 3, 7); -- 3");
        HELP_MAP.put("GREATEST()", "Returns the largest value from arguments.\nSyntax: GREATEST(v1, v2, ...);\nExample: SELECT GREATEST(10, 3, 7); -- 10");
        HELP_MAP.put("CEILING()", "Alias for CEIL().\nSyntax: CEILING(x);\nExample: SELECT CEILING(4.1); -- 5");

        // ── Functions: Date & Time extras ────────────────────────────────────────
        HELP_MAP.put("CURRENT_DATE()", "Alias for CURDATE().\nExample: SELECT CURRENT_DATE();");
        HELP_MAP.put("CURRENT_TIME()", "Alias for CURTIME().\nExample: SELECT CURRENT_TIME();");
        HELP_MAP.put("DATE()", "Extracts date part from DATETIME/TIMESTAMP.\nSyntax: DATE(datetime);\nExample: SELECT DATE(NOW()); -- '2026-08-31'");
        HELP_MAP.put("DAYOFMONTH()", "Alias for DAY().\nSyntax: DAYOFMONTH(date);\nExample: SELECT DAYOFMONTH('2026-08-31'); -- 31");
        HELP_MAP.put("HOUR()", "Extracts hour (0-23) from time/datetime.\nSyntax: HOUR(time);\nExample: SELECT HOUR('14:30:00'); -- 14");
        HELP_MAP.put("MINUTE()", "Extracts minute (0-59) from time/datetime.\nSyntax: MINUTE(time);\nExample: SELECT MINUTE('14:30:45'); -- 30");
        HELP_MAP.put("SECOND()", "Extracts seconds (0-59) from time/datetime.\nSyntax: SECOND(time);\nExample: SELECT SECOND('14:30:45'); -- 45");
        HELP_MAP.put("DATE_SUB()", "Subtracts a time interval from a date.\nSyntax: DATE_SUB(date, INTERVAL n unit);\nExample: SELECT DATE_SUB(NOW(), INTERVAL 30 DAY);");
        HELP_MAP.put("STR_TO_DATE()", "Parses a formatted date string into a DATE.\nSyntax: STR_TO_DATE(str, format);\nExample: SELECT STR_TO_DATE('31-08-2026', '%d-%m-%Y');");
        HELP_MAP.put("TIMESTAMPDIFF()", "Returns difference between two datetimes in specified unit.\nSyntax: TIMESTAMPDIFF(unit, dt1, dt2);\nExample: SELECT TIMESTAMPDIFF(YEAR, birth_date, NOW()) AS age FROM users;");
        HELP_MAP.put("ADDDATE()", "Alias for DATE_ADD().\nSyntax: ADDDATE(date, INTERVAL n unit);\nExample: SELECT ADDDATE('2026-08-01', INTERVAL 30 DAY);");
        HELP_MAP.put("SUBDATE()", "Alias for DATE_SUB().\nSyntax: SUBDATE(date, INTERVAL n unit);\nExample: SELECT SUBDATE(NOW(), INTERVAL 7 DAY);");
        HELP_MAP.put("EXTRACT()", "Extracts a date/time component.\nSyntax: EXTRACT(unit FROM date);\nExample: SELECT EXTRACT(YEAR FROM NOW()); SELECT EXTRACT(MONTH FROM order_date) FROM orders;");
        HELP_MAP.put("LAST_DAY()", "Returns the last day of the month for a given date.\nSyntax: LAST_DAY(date);\nExample: SELECT LAST_DAY('2026-02-01'); -- '2026-02-28'");
        HELP_MAP.put("MAKEDATE()", "Returns a date from year and day-of-year.\nSyntax: MAKEDATE(year, dayofyear);\nExample: SELECT MAKEDATE(2026, 243);");
        HELP_MAP.put("MAKETIME()", "Constructs TIME from h/m/s components.\nSyntax: MAKETIME(h, m, s);\nExample: SELECT MAKETIME(14, 30, 0); -- '14:30:00'");
        HELP_MAP.put("DAYNAME()", "Returns full weekday name.\nSyntax: DAYNAME(date);\nExample: SELECT DAYNAME('2026-08-31'); -- 'Monday'");
        HELP_MAP.put("WEEK()", "Returns week number of year (0-53).\nSyntax: WEEK(date [, mode]);\nExample: SELECT WEEK(NOW());");
        HELP_MAP.put("WEEKDAY()", "Returns weekday index (0=Monday, 6=Sunday).\nSyntax: WEEKDAY(date);\nExample: SELECT WEEKDAY('2026-08-31'); -- 0 (Monday)");

        // ── Functions: Conditional & Conversion extras ───────────────────────────
        HELP_MAP.put("NULLIF()", "Returns NULL if both args are equal; otherwise returns first arg.\nSyntax: NULLIF(v1, v2);\nExample: SELECT NULLIF(0, 0); -- NULL (avoids divide-by-zero)");
        HELP_MAP.put("CAST()", "Converts a value to a specified data type.\nSyntax: CAST(expr AS type);\nExample: SELECT CAST('42' AS INT); SELECT CAST(NOW() AS DATE);");
        HELP_MAP.put("CONVERT()", "Converts value to specified type or charset.\nSyntax: CONVERT(expr, type) or CONVERT(expr USING charset);\nExample: SELECT CONVERT('3.14', DECIMAL(10,2));");
        HELP_MAP.put("BINARY()", "Casts string to binary for case-sensitive comparison.\nSyntax: BINARY(string);\nExample: SELECT BINARY 'abc' = BINARY 'ABC'; -- 0");

        // ── Functions: Encryption & System ───────────────────────────────────────
        HELP_MAP.put("MD5()", "Returns MD5 hash as 32-char hex string.\nSyntax: MD5(string);\nExample: SELECT MD5('password');");
        HELP_MAP.put("SHA1()", "Returns SHA-1 hash as 40-char hex string.\nSyntax: SHA1(string);\nExample: SELECT SHA1('password');");
        HELP_MAP.put("SHA()", "Alias for SHA1().\nSyntax: SHA(string);");
        HELP_MAP.put("SHA2()", "Returns SHA-2 family hash (224/256/384/512).\nSyntax: SHA2(string, hash_length);\nExample: SELECT SHA2('password', 256);");
        HELP_MAP.put("AES_ENCRYPT()", "Encrypts data using AES algorithm.\nSyntax: AES_ENCRYPT(data, key);\nExample: SELECT AES_ENCRYPT('secret', 'mykey');");
        HELP_MAP.put("AES_DECRYPT()", "Decrypts AES-encrypted data.\nSyntax: AES_DECRYPT(ciphertext, key);\nExample: SELECT AES_DECRYPT(encrypted_col, 'mykey') FROM secure_data;");
        HELP_MAP.put("DATABASE()", "Returns the current active database name.\nExample: SELECT DATABASE();");
        HELP_MAP.put("VERSION()", "Returns the server version string.\nExample: SELECT VERSION();");
        HELP_MAP.put("CONNECTION_ID()", "Returns the unique connection ID for current session.\nExample: SELECT CONNECTION_ID();");
        HELP_MAP.put("SYSTEM_USER()", "Returns current authenticated user and host.\nExample: SELECT SYSTEM_USER();");
        HELP_MAP.put("SESSION_USER()", "Alias for SYSTEM_USER().\nExample: SELECT SESSION_USER();");
        HELP_MAP.put("CURRENT_USER()", "Returns the user the current session is authenticated as.\nExample: SELECT CURRENT_USER();");
        HELP_MAP.put("CHARSET()", "Returns the character set of a string expression.\nSyntax: CHARSET(str);\nExample: SELECT CHARSET(name) FROM users;");
        HELP_MAP.put("COLLATION()", "Returns the collation of a string expression.\nSyntax: COLLATION(str);\nExample: SELECT COLLATION(name) FROM users;");

        // ── Functions: JSON extras ───────────────────────────────────────────────
        HELP_MAP.put("JSON_SET()", "Inserts or updates JSON values at specified paths.\nSyntax: JSON_SET(doc, path, val [, path, val ...]);\nExample: SELECT JSON_SET('{\"a\":1}', '$.b', 2); -- '{\"a\":1,\"b\":2}'");
        HELP_MAP.put("JSON_REMOVE()", "Removes data from JSON document at specified paths.\nSyntax: JSON_REMOVE(doc, path [, path ...]);\nExample: SELECT JSON_REMOVE('{\"a\":1,\"b\":2}', '$.b'); -- '{\"a\":1}'");
        HELP_MAP.put("JSON_CONTAINS()", "Returns 1 if JSON document contains specified value.\nSyntax: JSON_CONTAINS(doc, val [, path]);\nExample: SELECT JSON_CONTAINS('[1,2,3]', '2'); -- 1");

        // ── Functions: Aggregate extras ──────────────────────────────────────────
        HELP_MAP.put("GROUP_CONCAT()", "Concatenates grouped values into a comma-separated string.\nSyntax: GROUP_CONCAT([DISTINCT] col [ORDER BY col] [SEPARATOR sep]);\nExample: SELECT dept, GROUP_CONCAT(name ORDER BY name SEPARATOR ', ') FROM employees GROUP BY dept;");

        // ── Window Functions extras ──────────────────────────────────────────────
        HELP_MAP.put("NTILE()", "Distributes rows into N equal-size buckets and returns bucket number.\nSyntax: NTILE(n) OVER (ORDER BY col);\nExample: SELECT name, salary, NTILE(4) OVER (ORDER BY salary) AS quartile FROM employees;");
        HELP_MAP.put("LAG()", "Returns value from a previous row within the window partition.\nSyntax: LAG(col [, offset [, default]]) OVER (ORDER BY col);\nExample: SELECT date, sales, LAG(sales,1) OVER (ORDER BY date) AS prev_sales FROM daily_sales;");
        HELP_MAP.put("LEAD()", "Returns value from a following row within the window partition.\nSyntax: LEAD(col [, offset [, default]]) OVER (ORDER BY col);\nExample: SELECT date, sales, LEAD(sales,1) OVER (ORDER BY date) AS next_sales FROM daily_sales;");
        HELP_MAP.put("FIRST_VALUE()", "Returns the first value in an ordered window frame.\nSyntax: FIRST_VALUE(col) OVER (PARTITION BY col ORDER BY col);\nExample: SELECT dept, salary, FIRST_VALUE(salary) OVER (PARTITION BY dept ORDER BY salary) AS lowest FROM employees;");
        HELP_MAP.put("LAST_VALUE()", "Returns the last value in an ordered window frame.\nSyntax: LAST_VALUE(col) OVER (PARTITION BY col ORDER BY col ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING);\nExample: SELECT dept, LAST_VALUE(salary) OVER (PARTITION BY dept ORDER BY salary ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS highest FROM employees;");
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
            if (desc == null && !cleanTopic.endsWith("()")) {
                desc = HELP_MAP.get(cleanTopic + "()");
                if (desc != null) {
                    cleanTopic = cleanTopic + "()";
                }
            }
            if (desc == null && cleanTopic.endsWith("()")) {
                String alt = cleanTopic.substring(0, cleanTopic.length() - 2);
                desc = HELP_MAP.get(alt);
                if (desc != null) {
                    cleanTopic = alt;
                }
            }
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
