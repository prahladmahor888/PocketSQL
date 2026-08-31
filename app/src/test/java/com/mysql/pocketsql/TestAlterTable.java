package com.mysql.pocketsql;

import org.junit.Test;
import static org.junit.Assert.*;
import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.QueryResult;
import com.mysql.pocketsql.engine.SqlKeywordSuggester;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class TestAlterTable {
    @Test
    public void testAlterTablePrimaryKeyValidationAndMetadata() throws Exception {
        File testDir = new File("build/test-pocketsql-alter-pk");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();
        
        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
        
        QueryResult createDb = engine.execute("CREATE DATABASE testdb;");
        assertTrue(createDb.message, createDb.success);
        
        QueryResult useDb = engine.execute("USE testdb;");
        assertTrue(useDb.message, useDb.success);
        
        QueryResult createTbl = engine.execute("CREATE TABLE users (in INT, name VARCHAR(255), age INT);");
        assertTrue(createTbl.message, createTbl.success);
        
        // 1. ALTER TABLE USERS ADD PRIMARY KEY (id) -> should fail because column 'id' does not exist
        QueryResult rErr = engine.execute("ALTER TABLE users ADD PRIMARY KEY (id);");
        assertFalse(rErr.success);
        assertTrue(rErr.message, rErr.message.contains("doesn't exist in table") || rErr.message.contains("does not exist"));
        
        // 2. ALTER TABLE USERS ADD PRIMARY KEY (in) -> should succeed
        QueryResult rSuccess = engine.execute("ALTER TABLE users ADD PRIMARY KEY (in);");
        assertTrue(rSuccess.success);
        
        // 3. DESC users -> should show Key = PRI and Null = NO for `in`
        QueryResult descRes = engine.execute("DESC users;");
        assertTrue(descRes.success);
        boolean foundIn = false;
        for (Map<String, Object> row : descRes.rows) {
            if ("in".equalsIgnoreCase(row.get("Field").toString())) {
                foundIn = true;
                assertEquals("PRI", row.get("Key"));
                assertEquals("NO", row.get("Null"));
            }
        }
        assertTrue(foundIn);
        
        // 4. Try adding duplicate primary key -> should fail
        QueryResult rDupErr = engine.execute("ALTER TABLE users ADD PRIMARY KEY (name);");
        assertFalse(rDupErr.success);
        assertTrue(rDupErr.message, rDupErr.message.contains("Multiple primary key defined"));
        
        // 5. DROP PRIMARY KEY -> should succeed
        QueryResult dropPk = engine.execute("ALTER TABLE users DROP PRIMARY KEY;");
        assertTrue(dropPk.message, dropPk.success);
        
        descRes = engine.execute("DESC users;");
        for (Map<String, Object> row : descRes.rows) {
            if ("in".equalsIgnoreCase(row.get("Field").toString())) {
                assertEquals("", row.get("Key"));
            }
        }
        
        // 6. DROP PRIMARY KEY again -> should fail with Can't DROP 'PRIMARY'
        QueryResult dropPkAgain = engine.execute("ALTER TABLE users DROP PRIMARY KEY;");
        assertFalse(dropPkAgain.success);
        assertTrue(dropPkAgain.message, dropPkAgain.message.contains("Can't DROP 'PRIMARY'"));
    }

    @Test
    public void testDropTableCaseInsensitive() throws Exception {
        File testDir = new File("build/test-pocketsql-drop-case");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();
        
        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
        
        engine.execute("CREATE DATABASE testdb2;");
        engine.execute("USE testdb2;");
        engine.execute("CREATE TABLE USERS (id INT);");
        
        QueryResult showRes = engine.execute("SHOW TABLES;");
        assertTrue(showRes.success);
        assertEquals(1, showRes.rows.size());
        
        QueryResult showCreate = engine.execute("SHOW CREATE TABLE users;");
        assertTrue(showCreate.message, showCreate.success);
        
        QueryResult dropRes = engine.execute("DROP TABLE users;");
        assertTrue(dropRes.message, dropRes.success);
        
        QueryResult showAfter = engine.execute("SHOW TABLES;");
        assertTrue(showAfter.success);
        assertEquals(0, showAfter.rows.size());
    }

    @Test
    public void testDuplicateIndexName() throws Exception {
        File testDir = new File("build/test-pocketsql-dup-idx");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();
        
        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
        
        engine.execute("CREATE DATABASE testdb3;");
        engine.execute("USE testdb3;");
        engine.execute("CREATE TABLE users (email VARCHAR(100), age INT, phone VARCHAR(20));");
        
        QueryResult add1 = engine.execute("ALTER TABLE users ADD UNIQUE (email);");
        assertTrue(add1.message, add1.success);
        
        // Adding UNIQUE on email again (unnamed) should fail with Duplicate key name 'uq_email'
        QueryResult add2 = engine.execute("ALTER TABLE users ADD UNIQUE (email);");
        assertFalse(add2.success);
        assertTrue(add2.message, add2.message.contains("Duplicate key name"));

        // Adding constraint on age should succeed
        QueryResult addAge = engine.execute("ALTER TABLE users ADD CONSTRAINT uq_user_age UNIQUE (age);");
        assertTrue(addAge.message, addAge.success);

        // Adding duplicate constraint name on another column should fail with Duplicate key name
        QueryResult addDupName = engine.execute("ALTER TABLE users ADD CONSTRAINT uq_user_age UNIQUE (phone);");
        assertFalse(addDupName.success);
        assertTrue(addDupName.message, addDupName.message.contains("Duplicate key name"));
    }

    @Test
    public void testCheckConstraintValidationAndDuplicates() throws Exception {
        File testDir = new File("build/test-pocketsql-chk-val");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE testdb4;");
        engine.execute("USE testdb4;");
        engine.execute("CREATE TABLE users (user_age INT);");

        // 1. ADD CHECK on non-existent column 'age' should fail
        QueryResult failCol = engine.execute("ALTER TABLE users ADD CONSTRAINT chk_age CHECK (age >= 18);");
        assertFalse(failCol.success);
        assertTrue(failCol.message, failCol.message.contains("doesn't exist in table"));

        // 2. ADD CHECK on existing column 'user_age' should succeed
        QueryResult okCheck = engine.execute("ALTER TABLE users ADD CONSTRAINT chk_age CHECK (user_age >= 18);");
        assertTrue(okCheck.message, okCheck.success);

        // 3. Duplicate ADD CHECK with same constraint name 'chk_age' should fail
        QueryResult dupCheck = engine.execute("ALTER TABLE users ADD CONSTRAINT chk_age CHECK (user_age >= 18);");
        assertFalse(dupCheck.success);
        assertTrue(dupCheck.message, dupCheck.message.contains("Duplicate constraint name"));

        // 4. Add second check with different name
        QueryResult okCheck2 = engine.execute("ALTER TABLE users ADD CONSTRAINT chk_age_2 CHECK (user_age <= 100);");
        assertTrue(okCheck2.message, okCheck2.success);

        // 5. DROP CONSTRAINT with batch comma-separated operations
        QueryResult dropBatch = engine.execute("ALTER TABLE users DROP CONSTRAINT chk_age, DROP CONSTRAINT chk_age_2;");
        assertTrue(dropBatch.message, dropBatch.success);
    }

    @Test
    public void testShowTableStatus() throws Exception {
        File testDir = new File("build/test-pocketsql-show-status");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE statusdb;");
        engine.execute("USE statusdb;");
        engine.execute("CREATE TABLE USERS (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50));");

        QueryResult resAll = engine.execute("SHOW TABLE STATUS;");
        assertTrue(resAll.message, resAll.success);
        assertEquals(1, resAll.rows.size());
        assertEquals("USERS", resAll.rows.get(0).get("Name"));

        QueryResult resLike = engine.execute("SHOW TABLE STATUS LIKE 'USERS';");
        assertTrue(resLike.message, resLike.success);
        assertEquals(1, resLike.rows.size());
        assertEquals("USERS", resLike.rows.get(0).get("Name"));
    }

    @Test
    public void testModifyColumnAutoIncrementCasing() throws Exception {
        File testDir = new File("build/test-pocketsql-modify-ai");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE modifydb;");
        engine.execute("USE modifydb;");
        engine.execute("CREATE TABLE USERS (ID INT PRIMARY KEY);");

        QueryResult modRes = engine.execute("ALTER TABLE USERS MODIFY COLUMN id INT NOT NULL AUTO_INCREMENT;");
        assertTrue(modRes.message, modRes.success);

        QueryResult descRes = engine.execute("DESC USERS;");
        assertTrue(descRes.message, descRes.success);
        boolean foundIdAi = false;
        for (Map<String, Object> r : descRes.rows) {
            if ("ID".equalsIgnoreCase(String.valueOf(r.get("Field")))) {
                assertEquals("auto_increment", r.get("Extra"));
                foundIdAi = true;
            }
        }
        assertTrue("Expected ID to have Extra = auto_increment", foundIdAi);
    }

    @Test
    public void testAlterTableAutoIncrementValue() throws Exception {
        File testDir = new File("build/test-pocketsql-alter-ai-val");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE aidb;");
        engine.execute("USE aidb;");
        engine.execute("CREATE TABLE USERS (ID INT PRIMARY KEY AUTO_INCREMENT, full_name VARCHAR(100));");

        engine.execute("INSERT INTO USERS (full_name) VALUES ('User 1');");
        QueryResult res1 = engine.execute("SELECT * FROM USERS;");
        assertEquals(1, res1.rows.size());
        assertEquals("1", String.valueOf(res1.rows.get(0).get("ID")));

        QueryResult alterRes = engine.execute("ALTER TABLE USERS AUTO_INCREMENT = 1000;");
        assertTrue(alterRes.message, alterRes.success);

        engine.execute("INSERT INTO USERS (full_name) VALUES ('Test User');");
        QueryResult res2 = engine.execute("SELECT * FROM USERS WHERE full_name = 'Test User';");
        assertEquals(1, res2.rows.size());
        assertEquals("1000", String.valueOf(res2.rows.get(0).get("ID")));
    }

    @Test
    public void testInsertIgnoreAndInsertSet() throws Exception {
        File testDir = new File("build/test-pocketsql-insert-extensions");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE insertdb;");
        engine.execute("USE insertdb;");
        engine.execute("CREATE TABLE USERS (id INT PRIMARY KEY AUTO_INCREMENT, full_name VARCHAR(100), email VARCHAR(100) UNIQUE, user_age INT, city VARCHAR(50), country VARCHAR(50));");

        // 1. INSERT IGNORE test
        QueryResult res1 = engine.execute("INSERT IGNORE INTO USERS (full_name, email) VALUES ('Rahul', 'rahul@gmail.com');");
        assertTrue(res1.message, res1.success);

        // Duplicate insert with IGNORE should succeed with 0 rows affected instead of throwing duplicate key error
        QueryResult resDup = engine.execute("INSERT IGNORE INTO USERS (full_name, email) VALUES ('Rahul Dup', 'rahul@gmail.com');");
        assertTrue(resDup.message, resDup.success);
        assertEquals(0, resDup.affectedRows);

        // 2. INSERT ... SET test
        QueryResult resSet = engine.execute("INSERT INTO USERS SET full_name = 'Suresh Kumar', user_age = 26, city = 'Bhopal', country = 'India', email = 'suresh@gmail.com';");
        assertTrue(resSet.message, resSet.success);
        assertEquals(1, resSet.affectedRows);

        QueryResult selectRes = engine.execute("SELECT * FROM USERS WHERE email = 'suresh@gmail.com';");
        assertTrue(selectRes.message, selectRes.success);
        assertEquals(1, selectRes.rows.size());
        assertEquals("Suresh Kumar", selectRes.rows.get(0).get("full_name"));
        assertEquals("Bhopal", selectRes.rows.get(0).get("city"));
    }

    @Test
    public void testUpdateCaseExpression() throws Exception {
        File testDir = new File("build/test-pocketsql-update-case");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE casedb;");
        engine.execute("USE casedb;");
        engine.execute("CREATE TABLE USERS (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50), city VARCHAR(50));");

        engine.execute("INSERT INTO USERS (name, city) VALUES ('User 1', 'Bhopal');");
        engine.execute("INSERT INTO USERS (name, city) VALUES ('User 2', 'Indore');");
        engine.execute("INSERT INTO USERS (name, city) VALUES ('User 3', 'Delhi');");

        QueryResult updRes = engine.execute("UPDATE USERS SET city = CASE WHEN city = 'Bhopal' THEN 'Indore' WHEN city = 'Indore' THEN 'Bhopal' ELSE city END;");
        assertTrue(updRes.message, updRes.success);

        QueryResult res1 = engine.execute("SELECT city FROM USERS WHERE name = 'User 1';");
        assertEquals("Indore", res1.rows.get(0).get("city"));

        QueryResult res2 = engine.execute("SELECT city FROM USERS WHERE name = 'User 2';");
        assertEquals("Bhopal", res2.rows.get(0).get("city"));

        QueryResult res3 = engine.execute("SELECT city FROM USERS WHERE name = 'User 3';");
        assertEquals("Delhi", res3.rows.get(0).get("city"));
    }

    @Test
    public void testUpdateAndDeleteWithLimitAndOrderBy() throws Exception {
        File testDir = new File("build/test-pocketsql-limit-update");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE limitdb;");
        engine.execute("USE limitdb;");
        engine.execute("CREATE TABLE USERS (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50), city VARCHAR(50), country VARCHAR(50));");

        engine.execute("INSERT INTO USERS (name, city, country) VALUES ('User A', 'City A', 'India');");
        engine.execute("INSERT INTO USERS (name, city, country) VALUES ('User B', 'City B', 'India');");
        engine.execute("INSERT INTO USERS (name, city, country) VALUES ('User C', 'City C', 'India');");

        // 1. UPDATE with LIMIT
        QueryResult updRes = engine.execute("UPDATE USERS SET city = 'Bhopal' WHERE country = 'India' LIMIT 2;");
        assertTrue(updRes.message, updRes.success);
        assertEquals(2, updRes.affectedRows);

        QueryResult checkBhopal = engine.execute("SELECT * FROM USERS WHERE city = 'Bhopal';");
        assertEquals(2, checkBhopal.rows.size());

        // 2. DELETE with ORDER BY and LIMIT
        QueryResult delRes = engine.execute("DELETE FROM USERS WHERE country = 'India' ORDER BY id DESC LIMIT 1;");
        assertTrue(delRes.message, delRes.success);
        assertEquals(1, delRes.affectedRows);

        QueryResult checkRemaining = engine.execute("SELECT * FROM USERS;");
        assertEquals(2, checkRemaining.rows.size());
    }

    @Test
    public void testExistsAndNotExistsSubquery() throws Exception {
        File testDir = new File("build/test-pocketsql-exists");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE existsdb;");
        engine.execute("USE existsdb;");
        engine.execute("CREATE TABLE users (id INT PRIMARY KEY AUTO_INCREMENT, full_name VARCHAR(100), city VARCHAR(50), gender VARCHAR(10));");

        engine.execute("INSERT INTO users (full_name, city, gender) VALUES ('Aman', 'Bhopal', 'Male');");
        engine.execute("INSERT INTO users (full_name, city, gender) VALUES ('Pooja', 'Bhopal', 'Female');");
        engine.execute("INSERT INTO users (full_name, city, gender) VALUES ('Karan', 'Indore', 'Male');");

        // 1. EXISTS test
        QueryResult resExists = engine.execute("SELECT u1.id, u1.full_name, u1.city FROM users u1 WHERE EXISTS (SELECT 1 FROM users u2 WHERE u2.city = u1.city AND u2.gender = 'Female');");
        assertTrue(resExists.message, resExists.success);
        assertEquals(2, resExists.rows.size()); // Aman and Pooja are both in Bhopal where a female exists

        // 2. NOT EXISTS test
        QueryResult resNotExists = engine.execute("SELECT u1.id, u1.full_name, u1.city FROM users u1 WHERE NOT EXISTS (SELECT 1 FROM users u2 WHERE u2.city = u1.city AND u2.gender = 'Female');");
        assertTrue(resNotExists.message, resNotExists.success);
        assertEquals(1, resNotExists.rows.size()); // Karan in Indore (no female in Indore)
        assertEquals("Karan", resNotExists.rows.get(0).get("full_name"));
    }

    @Test
    public void testGroupByWithRollup() throws Exception {
        File testDir = new File("build/test-pocketsql-rollup");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE rollupdb;");
        engine.execute("USE rollupdb;");
        engine.execute("CREATE TABLE users (id INT PRIMARY KEY AUTO_INCREMENT, country VARCHAR(50), city VARCHAR(50), user_age INT);");

        engine.execute("INSERT INTO users (country, city, user_age) VALUES ('India', 'Bhopal', 20);");
        engine.execute("INSERT INTO users (country, city, user_age) VALUES ('India', 'Indore', 30);");
        engine.execute("INSERT INTO users (country, city, user_age) VALUES ('USA', 'NY', 40);");

        String query = "SELECT country, city, COUNT(id) AS total_users, ROUND(AVG(user_age), 1) AS avg_age FROM users GROUP BY country, city WITH ROLLUP;";
        QueryResult res = engine.execute(query);

        assertTrue(res.message, res.success);
        assertEquals("Expected 6 rows but got " + res.rows.size() + ". Rows: " + res.rows, 6, res.rows.size()); // 3 base groups + 2 country subtotals + 1 grand total
    }

    @Test
    public void testSqlKeywordSuggesterNoDuplicates() {
        List<String> keywords = SqlKeywordSuggester.getKeywords();
        Set<String> uniqueKeywords = new HashSet<>(keywords);
        assertEquals("Duplicate keywords found in SqlKeywordSuggester list!", uniqueKeywords.size(), keywords.size());

        List<String> sugS = SqlKeywordSuggester.suggest("s");
        Set<String> uniqueSugS = new HashSet<>(sugS);
        assertEquals("Duplicate suggestions returned for prefix 's'!", uniqueSugS.size(), sugS.size());
    }

    @Test
    public void testStringAndBitwiseFunctions() throws Exception {
        File testDir = new File("build/test-pocketsql-bitwise");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE bitwisedb;");
        engine.execute("USE bitwisedb;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, full_name VARCHAR(100), phone VARCHAR(20), country VARCHAR(50), city VARCHAR(50), address VARCHAR(100), email VARCHAR(100));");
        engine.execute("INSERT INTO users VALUES (1, 'Rahul Sharma', '9876543210', 'India', 'Bhopal', 'MG Road', 'rahul@gmail.com');");

        String query = "SELECT " +
            "CONCAT(full_name, ' (', city, ')') AS user_info, " +
            "CONCAT_WS(' - ', full_name, phone, country) AS full_contact, " +
            "FORMAT(12500.756, 2) AS formatted_num, " +
            "QUOTE('O\\'Reilly') AS escaped_str, " +
            "UPPER(city) AS city_upper, " +
            "LOWER(email) AS email_lower, " +
            "LENGTH('PocketSQL') AS byte_len, " +
            "CHAR_LENGTH('PocketSQL') AS char_len, " +
            "SUBSTRING(email, 1, 5) AS sub_str, " +
            "SUBSTR(email, -3) AS sub_str_short, " +
            "LEFT(full_name, 4) AS name_left, " +
            "RIGHT(phone, 4) AS phone_last4, " +
            "TRIM('  Delhi  ') AS trimmed, " +
            "LTRIM('  Delhi') AS left_trimmed, " +
            "RTRIM('Delhi  ') AS right_trimmed, " +
            "LPAD(ID, 5, '0') AS padded_id, " +
            "RPAD(full_name, 20, '.') AS padded_name, " +
            "REPEAT('*', 5) AS repeated_stars, " +
            "REPLACE(email, '@gmail.com', '@work.com') AS new_email, " +
            "REVERSE(city) AS city_rev, " +
            "INSTR(email, '@') AS at_pos, " +
            "LOCATE('Road', address) AS road_pos, " +
            "HEX('SQL') AS hex_val, " +
            "UNHEX('53514C') AS unhex_val, " +
            "ASCII('A') AS ascii_a, " +
            "CHAR(65, 66, 67) AS chars_from_ascii, " +
            "FIELD(country, 'USA', 'UK', 'India') AS country_field_index, " +
            "FIND_IN_SET(city, 'Delhi,Bhopal,Kolkata') AS city_in_set, " +
            "ELT(2, 'First', 'Second', 'Third') AS elt_val, " +
            "MAKE_SET(1 | 4, 'A', 'B', 'C', 'D') AS set_val " +
            "FROM users WHERE ID = 1;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(1, res.rows.size());

        Map<String, Object> row = res.rows.get(0);
        assertEquals("Rahul Sharma (Bhopal)", row.get("user_info"));
        assertEquals("53514C", row.get("hex_val"));
        assertEquals("SQL", row.get("unhex_val"));
        assertEquals(65L, row.get("ascii_a"));
        assertEquals("ABC", row.get("chars_from_ascii"));
        assertEquals(3L, row.get("country_field_index"));
        assertEquals(2L, row.get("city_in_set"));
        assertEquals("Second", row.get("elt_val"));
        assertEquals("A,C", row.get("set_val"));
    }

    @Test
    public void testTruncateFunction() throws Exception {
        File testDir = new File("build/test-pocketsql-truncate");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        QueryResult res = engine.execute("SELECT TRUNCATE(123.4567, 2) AS truncated_val;");
        assertTrue(res.message, res.success);
        assertEquals(123.45, (Double) res.rows.get(0).get("truncated_val"), 0.0001);
    }

    @Test
    public void testMathFunctionsAndFlattenedComments() throws Exception {
        File testDir = new File("build/test-pocketsql-math");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE mathdb;");
        engine.execute("USE mathdb;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, user_age INT, age INT);");
        engine.execute("INSERT INTO users VALUES (1011, 25, 20);");

        String query = "SELECT " +
            "ABS(-15.5) AS abs_val, " +
            "ROUND(12.3456, 2) AS rounded, " +
            "CEIL(12.01) AS ceil_val, " +
            "CEILING(12.01) AS ceiling_val, " +
            "FLOOR(12.99) AS floor_val, " +
            "TRUNCATE(12.3456, 1) AS truncated, " +
            "MOD(user_age, 5) AS age_remainder, " +
            "POWER(2, 3) AS power_val, " +
            "POW(2, 3) AS pow_val, " +
            "SQRT(144) AS sqrt_val, " +
            "RAND() AS random_val, " +
            "SIGN(-25) AS sign_negative, " +
            "PI() AS pi_val, " +
            "EXP(1) AS exp_val, " +
            "LOG(10) AS natural_log, " +
            "LOG10(100) AS base10_log, " +
            "LOG2(8) AS base2_log, " +
            "DEGREES(PI()) AS deg, " +
            "RADIANS(180) AS rad, " +
            "SIN(RADIANS(90)) AS sin_90, " +
            "COS(0) AS cos_0, " +
            "TAN(RADIANS(45)) AS tan_45, " +
            "ASIN(1) AS asin_val, " +
            "ACOS(1) AS acos_val, " +
            "ATAN(1) AS atan_val, " +
            "LEAST(user_age, age, 30) AS lowest_val, " +
            "GREATEST(user_age, age, 30) AS highest_val " +
            "FROM users WHERE ID = 1011;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(1, res.rows.size());

        Map<String, Object> row = res.rows.get(0);
        assertEquals(2.0, (Double) row.get("base10_log"), 0.0001);
        assertEquals(3.0, (Double) row.get("base2_log"), 0.0001);
        assertEquals(180.0, (Double) row.get("deg"), 0.0001);
        assertEquals(1.0, (Double) row.get("sin_90"), 0.0001);
        assertEquals(20L, row.get("lowest_val"));
        assertEquals(30L, row.get("highest_val"));
    }

    @Test
    public void testDateTimeFunctionsAll() throws Exception {
        File testDir = new File("build/test-pocketsql-datetime");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        String query = "SELECT " +
            "NOW() AS current_dt, " +
            "CURDATE() AS cur_date, " +
            "CURRENT_DATE() AS current_date_val, " +
            "CURTIME() AS cur_time, " +
            "CURRENT_TIME() AS current_time_val, " +
            "DATE(NOW()) AS date_part, " +
            "TIME(NOW()) AS time_part, " +
            "YEAR('2026-08-31 10:30:00') AS yr, " +
            "MONTH('2026-08-31 10:30:00') AS mnth, " +
            "DAY('2026-08-31') AS dy, " +
            "DAYOFMONTH('2026-08-31') AS day_of_m, " +
            "HOUR('10:30:45') AS hr, " +
            "MINUTE('10:30:45') AS mint, " +
            "SECOND('10:30:45') AS sec, " +
            "DAYNAME('2026-08-31') AS day_name, " +
            "MONTHNAME('2026-08-31') AS month_name, " +
            "WEEK('2026-08-31') AS week_num, " +
            "WEEKDAY('2026-08-31') AS weekday_idx, " +
            "EXTRACT(YEAR FROM NOW()) AS extracted_yr, " +
            "LAST_DAY('2026-02-01') AS last_day_feb, " +
            "DATEDIFF('2026-08-31', '2026-08-01') AS diff_days, " +
            "DATE_ADD('2026-08-31', INTERVAL 7 DAY) AS date_added, " +
            "DATE_SUB('2026-08-31', INTERVAL 1 MONTH) AS date_subtracted, " +
            "ADDDATE('2026-08-31', 5) AS add_date_short, " +
            "SUBDATE('2026-08-31', 5) AS sub_date_short, " +
            "TIMESTAMPDIFF(YEAR, '1995-05-15', '2026-08-31') AS exact_age, " +
            "MAKEDATE(2026, 243) AS date_from_day, " +
            "MAKETIME(14, 30, 0) AS created_time, " +
            "DATE_FORMAT('2026-08-31 14:00:00', '%d-%M-%Y %W') AS formatted_dt, " +
            "STR_TO_DATE('31-08-2026', '%d-%m-%Y') AS parsed_date;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(1, res.rows.size());

        Map<String, Object> row = res.rows.get(0);
        assertEquals(2026L, row.get("yr"));
        assertEquals(8L, row.get("mnth"));
        assertEquals(31L, row.get("dy"));
        assertEquals(31L, row.get("day_of_m"));
        assertEquals(10L, row.get("hr"));
        assertEquals(30L, row.get("mint"));
        assertEquals(45L, row.get("sec"));
        assertEquals("2026-02-28", row.get("last_day_feb"));
        assertEquals(30L, row.get("diff_days"));
        assertEquals("2026-09-07 00:00:00", row.get("date_added"));
        assertEquals("2026-07-31 00:00:00", row.get("date_subtracted"));
        assertEquals("2026-09-05", row.get("add_date_short"));
        assertEquals("2026-08-26", row.get("sub_date_short"));
        assertEquals(31L, row.get("exact_age"));
        assertEquals("2026-08-31", row.get("date_from_day"));
        assertEquals("14:30:00", row.get("created_time"));
        assertEquals("31-August-2026 Monday", row.get("formatted_dt"));
        assertEquals("2026-08-31", row.get("parsed_date"));
    }

    @Test
    public void testConditionalConversionAndBinaryQuery() throws Exception {
        File testDir = new File("build/test-pocketsql-cond-conv");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE condDB;");
        engine.execute("USE condDB;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, full_name VARCHAR(50), user_age INT, phone VARCHAR(20), email VARCHAR(50), city VARCHAR(50));");
        engine.execute("INSERT INTO users VALUES (1, 'Rahul', 25, NULL, 'rahul@gmail.com', 'Bhopal');");

        String query = "SELECT " +
            "ID, " +
            "full_name, " +
            "IF(user_age >= 18, 'Adult', 'Minor') AS age_group, " +
            "IFNULL(phone, 'No Phone') AS contact_status, " +
            "NULLIF(city, 'Delhi') AS null_if_delhi, " +
            "COALESCE(phone, email, 'No Info') AS fallback_contact, " +
            "CAST(user_age AS CHAR) AS age_as_text, " +
            "CONVERT('2026-08-31', DATE) AS text_as_date, " +
            "BINARY 'Delhi' = 'delhi' AS case_sensitive_check " +
            "FROM users WHERE ID <= 5;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(1, res.rows.size());

        Map<String, Object> row = res.rows.get(0);
        System.out.println("row: " + row);
        assertEquals("Adult", row.get("age_group"));
        assertEquals("No Phone", row.get("contact_status"));
        assertEquals("Bhopal", row.get("null_if_delhi"));
        assertEquals("rahul@gmail.com", row.get("fallback_contact"));
        assertEquals("25", row.get("age_as_text"));
        assertEquals("2026-08-31", row.get("text_as_date"));
        assertEquals(false, row.get("case_sensitive_check"));
    }

    @Test
    public void testHashEncryptionAndSystemFunctionsQuery() throws Exception {
        File testDir = new File("build/test-pocketsql-hash-sys");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE sysDB;");
        engine.execute("USE sysDB;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, full_name VARCHAR(50));");
        engine.execute("INSERT INTO users VALUES (1, 'Rahul');");

        String query = "SELECT " +
            "MD5('Password123') AS md5_hash, " +
            "SHA1('Password123') AS sha1_hash, " +
            "SHA('Password123') AS sha_hash, " +
            "SHA2('Password123', 256) AS sha256_hash, " +
            "HEX(AES_ENCRYPT('SecretData', 'my_key')) AS encrypted_val, " +
            "AES_DECRYPT(AES_ENCRYPT('SecretData', 'my_key'), 'my_key') AS decrypted_val, " +
            "DATABASE() AS current_db, " +
            "VERSION() AS mysql_ver, " +
            "CONNECTION_ID() AS conn_id, " +
            "SYSTEM_USER() AS sys_user, " +
            "SESSION_USER() AS session_usr, " +
            "USER() AS user_val, " +
            "CURRENT_USER() AS curr_user, " +
            "CHARSET(full_name) AS char_set, " +
            "COLLATION(full_name) AS coll_val " +
            "FROM users WHERE ID = 1;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(1, res.rows.size());

        Map<String, Object> row = res.rows.get(0);
        assertNotNull(row.get("md5_hash"));
        assertNotNull(row.get("sha1_hash"));
        assertEquals(row.get("sha1_hash"), row.get("sha_hash"));
        assertNotNull(row.get("sha256_hash"));
        assertNotNull(row.get("encrypted_val"));
        assertEquals("SecretData", row.get("decrypted_val"));
        assertEquals("sysDB", row.get("current_db"));
        assertNotNull(row.get("mysql_ver"));
        assertTrue(row.get("mysql_ver").toString().contains("1.0."));
        assertEquals(1L, row.get("conn_id"));
        assertEquals("root@localhost", row.get("sys_user"));
        assertEquals("root@localhost", row.get("session_usr"));
        assertEquals("root@localhost", row.get("user_val"));
        assertEquals("root@localhost", row.get("curr_user"));
        assertEquals("utf8mb4", row.get("char_set"));
        assertEquals("utf8mb4_general_ci", row.get("coll_val"));
    }

    @Test
    public void testJsonFunctionsQuery() throws Exception {
        File testDir = new File("build/test-pocketsql-json-fn");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE jsonDB;");
        engine.execute("USE jsonDB;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, full_name VARCHAR(50), city VARCHAR(50), country VARCHAR(50), user_age INT);");
        engine.execute("INSERT INTO users VALUES (1, 'Aarav', 'Delhi', 'India', 25);");

        String query = "SELECT " +
            "JSON_OBJECT('id', ID, 'name', full_name, 'location', city) AS user_json, " +
            "JSON_ARRAY(city, country, user_age) AS array_json, " +
            "JSON_EXTRACT('{\"name\": \"Aarav\", \"skills\": [\"SQL\", \"Python\"]}', '$.skills[0]') AS first_skill, " +
            "JSON_CONTAINS('[\"SQL\", \"Python\", \"Java\"]', '\"SQL\"') AS has_sql, " +
            "JSON_SET('{\"name\": \"Aarav\"}', '$.city', 'Delhi', '$.name', 'Aarav Sharma') AS modified_json, " +
            "JSON_REMOVE('{\"name\": \"Aarav\", \"temp\": \"remove_me\"}', '$.temp') AS cleaned_json " +
            "FROM users WHERE ID = 1;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(1, res.rows.size());

        Map<String, Object> row = res.rows.get(0);
        assertNotNull(row.get("user_json"));
        assertNotNull(row.get("array_json"));
        assertEquals("SQL", row.get("first_skill"));
        assertEquals(1L, row.get("has_sql"));
        assertTrue(row.get("modified_json").toString().contains("Delhi"));
        assertFalse(row.get("cleaned_json").toString().contains("temp"));
    }

    @Test
    public void testGroupConcatWithOrderByAndSeparator() throws Exception {
        File testDir = new File("build/test-pocketsql-gc");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE gcDB;");
        engine.execute("USE gcDB;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, full_name VARCHAR(50), country VARCHAR(50), user_age INT);");
        engine.execute("INSERT INTO users VALUES (1, 'Aarav', 'India', 25);");
        engine.execute("INSERT INTO users VALUES (2, 'Bhavna', 'India', 30);");
        engine.execute("INSERT INTO users VALUES (3, 'Chetan', 'USA', 28);");

        String query = "SELECT " +
            "country, " +
            "COUNT(ID) AS total_users, " +
            "SUM(user_age) AS sum_age, " +
            "ROUND(AVG(user_age), 1) AS avg_age, " +
            "MIN(user_age) AS min_age, " +
            "MAX(user_age) AS max_age, " +
            "GROUP_CONCAT(full_name ORDER BY full_name SEPARATOR ', ') AS user_list " +
            "FROM users " +
            "GROUP BY country ORDER BY country;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(2, res.rows.size());

        Map<String, Object> indiaRow = res.rows.get(0);
        assertEquals("India", indiaRow.get("country"));
        assertEquals(2L, indiaRow.get("total_users"));
        assertEquals("Aarav, Bhavna", indiaRow.get("user_list"));

        Map<String, Object> usaRow = res.rows.get(1);
        assertEquals("USA", usaRow.get("country"));
        assertEquals(1L, usaRow.get("total_users"));
        assertEquals("Chetan", usaRow.get("user_list"));
    }

    @Test
    public void testWindowValueFunctionsLagLeadFirstLast() throws Exception {
        File testDir = new File("build/test-pocketsql-wf-val");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE wfDB;");
        engine.execute("USE wfDB;");
        engine.execute("CREATE TABLE users (ID INT PRIMARY KEY, full_name VARCHAR(50), country VARCHAR(50), city VARCHAR(50), user_age INT);");
        engine.execute("INSERT INTO users VALUES (101, 'Aman Gupta', 'India', 'Bhopal', 21);");
        engine.execute("INSERT INTO users VALUES (103, 'Pooja Verma', 'India', 'Delhi', 22);");
        engine.execute("INSERT INTO users VALUES (1020, 'Vikas Rao', 'India', 'Hyderabad', 38);");

        String query = "SELECT " +
            "ID, full_name, country, user_age, " +
            "ROW_NUMBER() OVER(PARTITION BY country ORDER BY user_age DESC) AS row_num, " +
            "LAG(user_age, 1) OVER(PARTITION BY country ORDER BY user_age) AS prev_younger_age, " +
            "LEAD(user_age, 1) OVER(PARTITION BY country ORDER BY user_age) AS next_older_age, " +
            "FIRST_VALUE(full_name) OVER(PARTITION BY country ORDER BY user_age) AS youngest_person, " +
            "LAST_VALUE(full_name) OVER(PARTITION BY country ORDER BY user_age ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS oldest_person " +
            "FROM users;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertEquals(3, res.rows.size());

        Map<String, Object> r1 = res.rows.get(0); // Aman Gupta (age 21)
        assertNull(r1.get("prev_younger_age"));
        assertEquals(22L, r1.get("next_older_age"));
        assertEquals("Aman Gupta", r1.get("youngest_person"));
        assertEquals("Vikas Rao", r1.get("oldest_person"));

        Map<String, Object> r2 = res.rows.get(1); // Pooja Verma (age 22)
        assertEquals(21L, r2.get("prev_younger_age"));
        assertEquals(38L, r2.get("next_older_age"));
        assertEquals("Aman Gupta", r2.get("youngest_person"));
        assertEquals("Vikas Rao", r2.get("oldest_person"));
    }

    @Test
    public void testBacktickIdentifiersAndCte() throws Exception {
        File testDir = new File("build/test-pocketsql-backticks-cte");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE company;");
        engine.execute("USE company;");
        engine.execute("CREATE TABLE products (`product_id` INT, `product_name` VARCHAR(50), `category` VARCHAR(50));");
        engine.execute("CREATE TABLE order_items (`product_id` INT, `quantity` INT, `unit_price` DOUBLE);");

        engine.execute("INSERT INTO products VALUES (1, 'Phone', 'Electronics'), (2, 'Laptop', 'Electronics'), (3, 'Shirt', 'Apparel');");
        engine.execute("INSERT INTO order_items VALUES (1, 2, 500.0), (2, 1, 1200.0), (3, 5, 20.0);");

        String cteQuery = "WITH RankedProducts AS (" +
            "    SELECT " +
            "        p.category, " +
            "        p.product_id, " +
            "        p.product_name, " +
            "        SUM(oi.quantity) AS units_sold, " +
            "        SUM(oi.quantity * oi.unit_price) AS total_revenue, " +
            "        DENSE_RANK() OVER (" +
            "            PARTITION BY p.category " +
            "            ORDER BY SUM(oi.quantity * oi.unit_price) DESC" +
            "        ) AS rank_in_category " +
            "    FROM products p " +
            "    INNER JOIN order_items oi ON p.product_id = oi.product_id " +
            "    GROUP BY p.category, p.product_id, p.product_name " +
            ") " +
            "SELECT " +
            "    category, " +
            "    rank_in_category AS `rank`, " +
            "    product_id, " +
            "    product_name, " +
            "    units_sold, " +
            "    total_revenue " +
            "FROM RankedProducts " +
            "WHERE rank_in_category <= 2 " +
            "ORDER BY category, rank_in_category;";

        QueryResult res = engine.execute(cteQuery);
        assertTrue(res.message, res.success);
        assertNotNull(res.rows);
        assertTrue(res.rows.size() > 0);
        assertTrue(res.columns.contains("rank"));
    }

    @Test
    public void testGroupByProjectionAlias() throws Exception {
        File testDir = new File("build/test-pocketsql-groupby-alias");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE sales_db;");
        engine.execute("USE sales_db;");
        engine.execute("CREATE TABLE orders (order_id INT, order_date VARCHAR(20));");
        engine.execute("CREATE TABLE order_items (order_id INT, quantity INT, unit_price DOUBLE);");

        engine.execute("INSERT INTO orders VALUES (1, '2026-08-15'), (2, '2026-08-20'), (3, '2026-09-01');");
        engine.execute("INSERT INTO order_items VALUES (1, 2, 100.0), (2, 3, 50.0), (3, 1, 300.0);");

        String query = "SELECT " +
            "    DATE_FORMAT(o.order_date, '%Y-%m') AS sales_month, " +
            "    COUNT(DISTINCT o.order_id) AS total_orders, " +
            "    SUM(oi.quantity) AS total_items_sold, " +
            "    SUM(oi.quantity * oi.unit_price) AS total_revenue " +
            "FROM orders o " +
            "INNER JOIN order_items oi ON o.order_id = oi.order_id " +
            "GROUP BY sales_month " +
            "ORDER BY sales_month;";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertNotNull(res.rows);
        assertEquals(2, res.rows.size());
        assertEquals("2026-08", res.rows.get(0).get("sales_month"));
        assertEquals(2L, res.rows.get(0).get("total_orders"));
        assertEquals(5.0, ((Number)res.rows.get(0).get("total_items_sold")).doubleValue(), 0.001);
        assertEquals(350.0, ((Number)res.rows.get(0).get("total_revenue")).doubleValue(), 0.001);
    }

    @Test
    public void testOrderByFunctionExpressions() throws Exception {
        File testDir = new File("build/test-pocketsql-orderby-expr");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        engine.execute("CREATE DATABASE sales_db2;");
        engine.execute("USE sales_db2;");
        engine.execute("CREATE TABLE orders (order_id INT, order_date VARCHAR(20));");
        engine.execute("CREATE TABLE order_items (order_id INT, quantity INT, unit_price DOUBLE);");

        engine.execute("INSERT INTO orders VALUES (1, '2026-08-15'), (2, '2026-08-20'), (3, '2026-09-01');");
        engine.execute("INSERT INTO order_items VALUES (1, 2, 100.0), (2, 3, 50.0), (3, 1, 300.0);");

        String query = "SELECT " +
            "    CONCAT(MONTHNAME(o.order_date), ' ', YEAR(o.order_date)) AS sales_month, " +
            "    COUNT(DISTINCT o.order_id) AS total_orders, " +
            "    SUM(oi.quantity * oi.unit_price) AS total_revenue " +
            "FROM orders o " +
            "INNER JOIN order_items oi ON o.order_id = oi.order_id " +
            "GROUP BY YEAR(o.order_date), MONTH(o.order_date), sales_month " +
            "ORDER BY YEAR(o.order_date), MONTH(o.order_date);";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertNotNull(res.rows);
        assertEquals(2, res.rows.size());
        assertEquals("August 2026", res.rows.get(0).get("sales_month"));
        assertEquals("September 2026", res.rows.get(1).get("sales_month"));
    }

    @Test
    public void testShowVariablesAndSetGlobal() throws Exception {
        File testDir = new File("build/test-pocketsql-show-vars");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        QueryResult resSet = engine.execute("SET GLOBAL log_bin_trust_function_creators = 1;");
        assertTrue(resSet.message, resSet.success);

        QueryResult resShow = engine.execute("SHOW VARIABLES LIKE 'log_bin_trust_function_creators';");
        assertTrue(resShow.message, resShow.success);
        assertNotNull(resShow.rows);
        assertEquals(1, resShow.rows.size());
        assertEquals("log_bin_trust_function_creators", resShow.rows.get(0).get("Variable_name"));
        assertEquals("1", resShow.rows.get(0).get("Value"));

        QueryResult resShowAll = engine.execute("SHOW GLOBAL VARIABLES;");
        assertTrue(resShowAll.message, resShowAll.success);
        assertTrue(resShowAll.rows.size() >= 10);

        QueryResult resSelectGlobal = engine.execute("SELECT @@GLOBAL.log_bin_trust_function_creators AS is_enabled;");
        assertTrue(resSelectGlobal.message, resSelectGlobal.success);
        assertNotNull(resSelectGlobal.rows);
        assertEquals(1, resSelectGlobal.rows.size());
        assertEquals("1", resSelectGlobal.rows.get(0).get("is_enabled"));
    }

    @Test
    public void testInformationSchemaRoutines() throws Exception {
        File testDir = new File("build/test-pocketsql-routines");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
        engine.execute("CREATE DATABASE test_db;");
        engine.execute("USE test_db;");

        String query = "SELECT " +
            "    ROUTINE_NAME AS function_name, " +
            "    DATA_TYPE AS return_type, " +
            "    CREATED, " +
            "    LAST_ALTERED " +
            "FROM information_schema.ROUTINES " +
            "WHERE ROUTINE_TYPE = 'FUNCTION' " +
            "  AND ROUTINE_SCHEMA = DATABASE();";

        QueryResult res = engine.execute(query);
        assertTrue(res.message, res.success);
        assertNotNull(res.rows);
    }

    @Test
    public void testSqliteMasterAndPragma() throws Exception {
        File testDir = new File("build/test-pocketsql-pragma");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
        engine.execute("CREATE DATABASE company;");
        engine.execute("USE company;");
        engine.execute("CREATE TABLE users (user_id INT PRIMARY KEY, user_name VARCHAR(100));");

        QueryResult resMaster = engine.execute("SELECT name, type FROM sqlite_master WHERE type='table';");
        assertTrue(resMaster.message, resMaster.success);
        assertNotNull(resMaster.rows);
        assertEquals(1, resMaster.rows.size());
        assertEquals("users", resMaster.rows.get(0).get("name"));
        assertEquals("table", resMaster.rows.get(0).get("type"));

        QueryResult resPragma = engine.execute("PRAGMA table_info(users);");
        assertTrue(resPragma.message, resPragma.success);
        assertNotNull(resPragma.rows);
        assertEquals(2, resPragma.rows.size());
        assertEquals("user_id", resPragma.rows.get(0).get("name"));
        assertEquals("user_name", resPragma.rows.get(1).get("name"));

        QueryResult resShowLike = engine.execute("SHOW DATABASES LIKE 'company';");
        assertTrue(resShowLike.message, resShowLike.success);
        assertNotNull(resShowLike.rows);
        assertEquals(1, resShowLike.rows.size());
        assertEquals("company", resShowLike.rows.get(0).get("Database"));

        QueryResult resShowSchemas = engine.execute("SHOW SCHEMAS LIKE 'nonexistent%';");
        assertTrue(resShowSchemas.message, resShowSchemas.success);
        assertNotNull(resShowSchemas.rows);
        assertEquals(0, resShowSchemas.rows.size());
    }
    
    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) deleteRecursive(c);
        }
        f.delete();
    }
}
