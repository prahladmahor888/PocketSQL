package com.mysql.pocketsql;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.mysql.pocketsql.engine.*;

public class SqlEngineTest {

    private DatabaseEngine engine;
    private File testDir;

    @Before
    public void setUp() {
        testDir = new File("build/test-pocketsql");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();
        engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
    }

    @After
    public void tearDown() {
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteRecursive(c);
            }
        }
        f.delete();
    }

    @Test
    public void testDatabaseLifecycle() {
        QueryResult r = engine.execute("SHOW DATABASES;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());

        // Create
        r = engine.execute("CREATE DATABASE my_db;");
        assertTrue(r.success);
        assertTrue(r.message.contains("created successfully"));

        // Show
        r = engine.execute("SHOW DATABASES;");
        assertTrue(r.success);
        assertEquals(4, r.rows.size());
        boolean foundMyDb = false;
        for (Map<String, Object> row : r.rows) {
            if ("my_db".equals(row.get("Database"))) {
                foundMyDb = true;
            }
        }
        assertTrue("my_db not found in SHOW DATABASES", foundMyDb);

        // Use
        r = engine.execute("USE my_db;");
        assertTrue(r.success);
        assertEquals("Database changed", r.message);
        assertEquals("my_db", engine.getActiveDatabase());

        // Drop
        r = engine.execute("DROP DATABASE my_db;");
        assertTrue(r.success);
        assertNull(engine.getActiveDatabase());

        r = engine.execute("SHOW DATABASES;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
    }

    @Test
    public void testTableLifecycleAndSchemaValidation() {
        engine.execute("CREATE DATABASE school;");
        engine.execute("USE school;");

        // Create Table
        QueryResult r = engine.execute("CREATE TABLE users (id INT, name TEXT, balance DOUBLE);");
        assertTrue(r.success);

        // Describe Table
        r = engine.execute("DESCRIBE users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("id", r.rows.get(0).get("Field"));
        assertEquals("INT", r.rows.get(0).get("Type"));
        assertEquals("name", r.rows.get(1).get("Field"));
        assertEquals("TEXT", r.rows.get(1).get("Type"));
        assertEquals("balance", r.rows.get(2).get("Field"));
        assertEquals("DOUBLE", r.rows.get(2).get("Type"));

        // Valid Insert positional
        r = engine.execute("INSERT INTO users VALUES (1, 'Amit', 150.75);");
        assertTrue(r.success);
        assertEquals(1, r.affectedRows);

        // Valid Insert column specific
        r = engine.execute("INSERT INTO users (name, id) VALUES ('Rahul', 2);");
        assertTrue(r.success);
        assertEquals(1, r.affectedRows);

        // Type check validation: Decimal to INT should fail
        r = engine.execute("INSERT INTO users VALUES (3.14, 'ErrorPerson', 200.0);");
        assertFalse(r.success);
        assertTrue(r.message.contains("Type mismatch") || r.message.contains("Syntax Error") || r.message.contains("Cannot store decimal") || r.message.contains("integer") || r.message.contains("convert"));

        // Type check validation: Non-numeric to INT should fail
        r = engine.execute("INSERT INTO users VALUES ('abc', 'ErrorPerson2', 200.0);");
        assertFalse(r.success);
    }

    @Test
    public void testQueryOperations() {
        engine.execute("CREATE DATABASE shop;");
        engine.execute("USE shop;");
        engine.execute("CREATE TABLE items (id INT, item_name TEXT, price DOUBLE);");

        // Multi-insert
        QueryResult r = engine.execute("INSERT INTO items VALUES (1, 'Apple', 1.2), (2, 'Banana', 0.8), (3, 'Cherry', 3.0), (4, 'Date', 2.5);");
        assertTrue(r.success);
        assertEquals(4, r.affectedRows);

        // Select All
        r = engine.execute("SELECT * FROM items;");
        assertTrue(r.success);
        assertEquals(4, r.rows.size());

        // Select with WHERE filter
        r = engine.execute("SELECT item_name, price FROM items WHERE price > 1.0;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size()); // Apple, Cherry, Date
        assertEquals("Apple", r.rows.get(0).get("item_name"));
        assertNull(r.rows.get(0).get("id"));

        // Select with LIKE filter
        r = engine.execute("SELECT * FROM items WHERE item_name LIKE '%an%';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("Banana", r.rows.get(0).get("item_name"));

        // Select with Sort (ORDER BY DESC)
        r = engine.execute("SELECT * FROM items ORDER BY price DESC;");
        assertTrue(r.success);
        assertEquals("Cherry", r.rows.get(0).get("item_name")); // 3.0
        assertEquals("Date", r.rows.get(1).get("item_name"));   // 2.5
        assertEquals("Apple", r.rows.get(2).get("item_name"));  // 1.2
        assertEquals("Banana", r.rows.get(3).get("item_name")); // 0.8

        // Select with LIMIT
        r = engine.execute("SELECT * FROM items ORDER BY id ASC LIMIT 2;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("Apple", r.rows.get(0).get("item_name"));
        assertEquals("Banana", r.rows.get(1).get("item_name"));
    }

    @Test
    public void testUpdateAndDelete() {
        engine.execute("CREATE DATABASE blog;");
        engine.execute("USE blog;");
        engine.execute("CREATE TABLE posts (id INT, title TEXT, likes INT);");
        engine.execute("INSERT INTO posts VALUES (1, 'Post 1', 10), (2, 'Post 2', 20), (3, 'Post 3', 30);");

        // Update with WHERE
        QueryResult r = engine.execute("UPDATE posts SET likes = 25 WHERE id = 2;");
        assertTrue(r.success);
        assertEquals(1, r.affectedRows);

        r = engine.execute("SELECT likes FROM posts WHERE id = 2;");
        assertTrue(r.success);
        assertEquals(25L, r.rows.get(0).get("likes"));

        // Delete with WHERE
        r = engine.execute("DELETE FROM posts WHERE likes > 26;");
        assertTrue(r.success);
        assertEquals(1, r.affectedRows); // Deleted Post 3

        r = engine.execute("SELECT * FROM posts;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
    }

    @Test
    public void testUserCreationAndPrivileges() {
        // 1. Create a database as root
        QueryResult r = engine.execute("CREATE DATABASE shop_priv;");
        assertTrue(r.success);
        
        r = engine.execute("USE shop_priv;");
        assertTrue(r.success);

        r = engine.execute("CREATE TABLE products (id INT, name TEXT);");
        assertTrue(r.success);
        
        r = engine.execute("INSERT INTO products VALUES (1, 'Widget');");
        assertTrue(r.success);

        // 2. Create user as root
        r = engine.execute("CREATE USER 'app_user'@'localhost' IDENTIFIED BY 'StrongPass123';");
        assertTrue(r.success);

        // 3. Grant select/insert privileges
        r = engine.execute("GRANT SELECT, INSERT ON shop_priv.* TO 'app_user'@'localhost';");
        assertTrue(r.success);

        r = engine.execute("FLUSH PRIVILEGES;");
        assertTrue(r.success);

        // 4. Test authenticate
        boolean authed = engine.authenticate("app_user", "StrongPass123");
        assertTrue(authed);
        assertEquals("app_user", engine.getCurrentUser());

        // 5. Test executing SELECT (should be allowed)
        r = engine.execute("SELECT * FROM products;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());

        // 6. Test executing UPDATE (should be denied because only SELECT/INSERT were granted)
        r = engine.execute("UPDATE products SET name = 'Gadget' WHERE id = 1;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Access denied") || r.message.contains("privilege"));

        // 7. Test invalid password login
        engine.setCurrentUser("root", "localhost"); // switch back to root
        authed = engine.authenticate("app_user", "WrongPass");
        assertFalse(authed);
    }

    @Test
    public void testDefaultValuesAndOnUpdate() throws Exception {
        engine.execute("CREATE DATABASE db_defaults;");
        engine.execute("USE db_defaults;");

        // 1. Create table with various DEFAULT value types and ON UPDATE
        QueryResult r = engine.execute(
            "CREATE TABLE employees (" +
            "  id INT PRIMARY KEY AUTO_INCREMENT," +
            "  name VARCHAR(100) DEFAULT 'Unknown'," +
            "  age INT DEFAULT 18," +
            "  country VARCHAR(50) DEFAULT 'India'," +
            "  salary DECIMAL(10,2) DEFAULT 0.00," +
            "  status ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE'," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ");"
        );
        assertTrue(r.success);

        // 2. Describe Table validation
        r = engine.execute("DESCRIBE employees;");
        assertTrue(r.success);
        assertEquals(8, r.rows.size());
        
        // Check Field, Type, Null, Key, Default, Extra columns
        assertEquals("id", r.rows.get(0).get("Field"));
        assertEquals("INT", r.rows.get(0).get("Type"));
        assertEquals("NO", r.rows.get(0).get("Null"));
        assertEquals("PRI", r.rows.get(0).get("Key"));
        assertNull(r.rows.get(0).get("Default"));
        assertEquals("auto_increment", r.rows.get(0).get("Extra"));

        assertEquals("name", r.rows.get(1).get("Field"));
        assertEquals("VARCHAR(100)", r.rows.get(1).get("Type"));
        assertEquals("YES", r.rows.get(1).get("Null"));
        assertEquals("Unknown", r.rows.get(1).get("Default"));

        assertEquals("age", r.rows.get(2).get("Field"));
        assertEquals("18", r.rows.get(2).get("Default"));

        assertEquals("country", r.rows.get(3).get("Field"));
        assertEquals("India", r.rows.get(3).get("Default"));

        assertEquals("salary", r.rows.get(4).get("Field"));
        assertEquals("0.00", r.rows.get(4).get("Default"));

        assertEquals("status", r.rows.get(5).get("Field"));
        assertEquals("ACTIVE", r.rows.get(5).get("Default"));

        assertEquals("created_at", r.rows.get(6).get("Field"));
        assertEquals("CURRENT_TIMESTAMP", r.rows.get(6).get("Default"));

        assertEquals("updated_at", r.rows.get(7).get("Field"));
        assertEquals("CURRENT_TIMESTAMP", r.rows.get(7).get("Default"));
        assertEquals("DEFAULT_GENERATED on update CURRENT_TIMESTAMP", r.rows.get(7).get("Extra"));

        // 3. Test insert with empty VALUES list ()
        r = engine.execute("INSERT INTO employees VALUES ();");
        assertTrue(r.success);

        r = engine.execute("SELECT * FROM employees;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        Map<String, Object> row = r.rows.get(0);
        assertEquals(1L, row.get("id"));
        assertEquals("Unknown", row.get("name"));
        assertEquals(18L, row.get("age"));
        assertEquals("India", row.get("country"));
        assertEquals(0.00, row.get("salary"));
        assertEquals("ACTIVE", row.get("status"));
        assertNotNull(row.get("created_at"));
        assertNotNull(row.get("updated_at"));

        // 4. Test insert with specific columns omitted and DEFAULT keyword
        r = engine.execute("INSERT INTO employees (name, age, status) VALUES (DEFAULT, 25, 'INACTIVE');");
        assertTrue(r.success);

        r = engine.execute("SELECT * FROM employees WHERE id = 2;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        row = r.rows.get(0);
        assertEquals(2L, row.get("id"));
        assertEquals("Unknown", row.get("name"));
        assertEquals(25L, row.get("age"));
        assertEquals("INACTIVE", row.get("status"));
        assertEquals("India", row.get("country"));

        // 5. Test ON UPDATE CURRENT_TIMESTAMP trigger
        String oldUpdatedAt = (String) row.get("updated_at");
        
        // Wait a small moment to ensure clock time ticks, then update
        Thread.sleep(1100);
        r = engine.execute("UPDATE employees SET age = 30 WHERE id = 2;");
        assertTrue(r.success);

        r = engine.execute("SELECT * FROM employees WHERE id = 2;");
        assertTrue(r.success);
        row = r.rows.get(0);
        assertEquals(30L, row.get("age"));
        String newUpdatedAt = (String) row.get("updated_at");
        assertNotEquals(oldUpdatedAt, newUpdatedAt);
    }

    @Test
    public void testMySQLConstraintsEnforcement() {
        engine.execute("CREATE DATABASE db_constraints;");
        engine.execute("USE db_constraints;");

        // 1. NOT NULL constraint test
        engine.execute("CREATE TABLE t_notnull (id INT PRIMARY KEY, name VARCHAR(100) NOT NULL);");
        QueryResult r = engine.execute("INSERT INTO t_notnull VALUES (1, NULL);");
        assertFalse(r.success);
        assertTrue(r.message.contains("cannot be null") || r.message.contains("Column"));

        r = engine.execute("INSERT INTO t_notnull VALUES (1, 'Ramesh');");
        assertTrue(r.success);

        // 2. PRIMARY KEY / UNIQUE duplicate check
        r = engine.execute("INSERT INTO t_notnull VALUES (1, 'Suresh');");
        assertFalse(r.success);
        assertTrue(r.message.contains("Duplicate entry") || r.message.contains("key"));

        // UNIQUE check
        engine.execute("CREATE TABLE t_unique (id INT, email VARCHAR(100) UNIQUE);");
        engine.execute("INSERT INTO t_unique VALUES (1, 'test@test.com');");
        r = engine.execute("INSERT INTO t_unique VALUES (2, 'test@test.com');");
        assertFalse(r.success);
        assertTrue(r.message.contains("Duplicate entry"));
        
        // UNIQUE allows multiple NULLs!
        r = engine.execute("INSERT INTO t_unique VALUES (3, NULL);");
        assertTrue(r.success);
        r = engine.execute("INSERT INTO t_unique VALUES (4, NULL);");
        assertTrue(r.success);

        // 3. CHECK constraint tests
        engine.execute("CREATE TABLE t_check (id INT PRIMARY KEY, age INT CHECK(age >= 18), salary DECIMAL(10,2) CHECK(salary > 0), percentage INT CHECK(percentage BETWEEN 0 AND 100), gender VARCHAR(10) CHECK(gender IN ('Male','Female')));");
        
        // Check simple comparison failure
        r = engine.execute("INSERT INTO t_check VALUES (1, 17, 100.0, 50, 'Male');");
        assertFalse(r.success);
        assertTrue(r.message.contains("constraint") || r.message.contains("violated"));

        // Check simple comparison success
        r = engine.execute("INSERT INTO t_check VALUES (1, 18, 100.0, 50, 'Male');");
        assertTrue(r.success);

        // Check salary comparison failure
        r = engine.execute("INSERT INTO t_check VALUES (2, 20, 0.0, 50, 'Male');");
        assertFalse(r.success);

        // Check BETWEEN failure
        r = engine.execute("INSERT INTO t_check VALUES (3, 20, 100.0, 101, 'Male');");
        assertFalse(r.success);

        // Check IN failure
        r = engine.execute("INSERT INTO t_check VALUES (4, 20, 100.0, 50, 'Other');");
        assertFalse(r.success);

        // Success insert
        r = engine.execute("INSERT INTO t_check VALUES (5, 20, 1000.00, 85, 'Female');");
        assertTrue(r.success);

        // 4. FOREIGN KEY constraint tests
        engine.execute("CREATE TABLE parents (id INT PRIMARY KEY, name VARCHAR(100));");
        engine.execute("CREATE TABLE children (child_id INT PRIMARY KEY, parent_id INT, FOREIGN KEY (parent_id) REFERENCES parents(id));");
        
        engine.execute("INSERT INTO parents VALUES (10, 'Parent 10');");

        // Insert foreign key reference that doesn't exist (fails)
        r = engine.execute("INSERT INTO children VALUES (1, 20);");
        assertFalse(r.success);
        assertTrue(r.message.contains("foreign key constraint fails"));

        // Insert foreign key reference that exists (succeeds)
        r = engine.execute("INSERT INTO children VALUES (1, 10);");
        assertTrue(r.success);

        // Test UPDATE violating constraints
        r = engine.execute("UPDATE children SET parent_id = 20 WHERE child_id = 1;");
        assertFalse(r.success);
        assertTrue(r.message.contains("foreign key constraint fails"));
        
        r = engine.execute("UPDATE t_check SET age = 15 WHERE id = 1;");
        assertFalse(r.success);
        assertTrue(r.message.contains("constraint") || r.message.contains("violated"));

        // 5. UNSIGNED constraint test
        engine.execute("CREATE TABLE t_unsigned (id INT PRIMARY KEY, quantity INT UNSIGNED, price DECIMAL(10,2) UNSIGNED);");
        
        // Negative quantity (fails)
        r = engine.execute("INSERT INTO t_unsigned VALUES (1, -5, 10.50);");
        assertFalse(r.success);
        assertTrue(r.message.contains("UNSIGNED") || r.message.contains("negative"));

        // Negative price (fails)
        r = engine.execute("INSERT INTO t_unsigned VALUES (2, 5, -10.50);");
        assertFalse(r.success);
        assertTrue(r.message.contains("UNSIGNED") || r.message.contains("negative"));

        // Success insert
        r = engine.execute("INSERT INTO t_unsigned VALUES (3, 5, 10.50);");
        assertTrue(r.success);

        // Update to negative (fails)
        r = engine.execute("UPDATE t_unsigned SET quantity = -1 WHERE id = 3;");
        assertFalse(r.success);
    }

    @Test
    public void testMySQLCompositeKeys() {
        engine.execute("CREATE DATABASE db_composite_keys;");
        engine.execute("USE db_composite_keys;");

        // 1. Composite PRIMARY KEY test
        QueryResult r = engine.execute(
            "CREATE TABLE student_courses (" +
            "  student_id INT," +
            "  course_id INT," +
            "  PRIMARY KEY (student_id, course_id)" +
            ");"
        );
        assertTrue(r.success);

        // Verify DESCRIBE shows PRI and NO nulls
        r = engine.execute("DESCRIBE student_courses;");
        assertTrue(r.success);
        assertEquals("student_id", r.rows.get(0).get("Field"));
        assertEquals("NO", r.rows.get(0).get("Null"));
        assertEquals("PRI", r.rows.get(0).get("Key"));
        assertEquals("course_id", r.rows.get(1).get("Field"));
        assertEquals("NO", r.rows.get(1).get("Null"));
        assertEquals("PRI", r.rows.get(1).get("Key"));

        // Insert valid records
        r = engine.execute("INSERT INTO student_courses VALUES (1, 101);");
        assertTrue(r.success);

        r = engine.execute("INSERT INTO student_courses VALUES (1, 102);"); // Same student, different course
        assertTrue(r.success);

        r = engine.execute("INSERT INTO student_courses VALUES (2, 101);"); // Different student, same course
        assertTrue(r.success);

        // Duplicate primary key combination (should fail)
        r = engine.execute("INSERT INTO student_courses VALUES (1, 101);");
        assertFalse(r.success);
        assertTrue(r.message.contains("Duplicate entry '1-101' for key 'PRIMARY'"));

        // Try inserting nulls in primary key columns (should fail because they are not null)
        r = engine.execute("INSERT INTO student_courses VALUES (NULL, 103);");
        assertFalse(r.success);
        assertTrue(r.message.contains("cannot be null") || r.message.contains("Column"));

        r = engine.execute("INSERT INTO student_courses VALUES (3, NULL);");
        assertFalse(r.success);
        assertTrue(r.message.contains("cannot be null") || r.message.contains("Column"));

        // 2. Composite UNIQUE KEY test
        r = engine.execute(
            "CREATE TABLE user_accounts (" +
            "  id INT PRIMARY KEY," +
            "  email VARCHAR(100)," +
            "  username VARCHAR(50)," +
            "  UNIQUE (email, username)" +
            ");"
        );
        assertTrue(r.success);

        r = engine.execute("DESCRIBE user_accounts;");
        assertTrue(r.success);
        assertEquals("UNI", r.rows.get(1).get("Key"));
        assertEquals("UNI", r.rows.get(2).get("Key"));

        // Insert valid unique combinations
        r = engine.execute("INSERT INTO user_accounts VALUES (1, 'user@test.com', 'user1');");
        assertTrue(r.success);

        r = engine.execute("INSERT INTO user_accounts VALUES (2, 'user@test.com', 'user2');"); // Same email, different username
        assertTrue(r.success);

        r = engine.execute("INSERT INTO user_accounts VALUES (3, 'other@test.com', 'user1');"); // Different email, same username
        assertTrue(r.success);

        // Duplicate unique combination (should fail)
        r = engine.execute("INSERT INTO user_accounts VALUES (4, 'user@test.com', 'user1');");
        assertFalse(r.success);
        assertTrue(r.message.contains("Duplicate entry 'user@test.com-user1' for key 'email'"));

        // NULL values in composite unique key (should bypass uniqueness check)
        r = engine.execute("INSERT INTO user_accounts VALUES (5, NULL, 'user3');");
        assertTrue(r.success);

        r = engine.execute("INSERT INTO user_accounts VALUES (6, NULL, 'user3');"); // Another NULL in same group bypasses check
        assertTrue(r.success);

        r = engine.execute("INSERT INTO user_accounts VALUES (7, 'user@test.com', NULL);");
        assertTrue(r.success);

        r = engine.execute("INSERT INTO user_accounts VALUES (8, 'user@test.com', NULL);"); // Another NULL in same group bypasses check
        assertTrue(r.success);

        // 3. Test UPDATE violations with composite keys
        r = engine.execute("UPDATE student_courses SET course_id = 101 WHERE course_id = 102;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Duplicate entry '1-101' for key 'PRIMARY'"));

        r = engine.execute("UPDATE user_accounts SET username = 'user1' WHERE id = 2;"); // email='user@test.com', username becomes 'user1', which duplicates id=1
        assertFalse(r.success);
        assertTrue(r.message.contains("Duplicate entry 'user@test.com-user1' for key 'email'"));

        // Valid update
        r = engine.execute("UPDATE student_courses SET course_id = 105 WHERE course_id = 102;");
        assertTrue(r.success);
    }

    @Test
    public void testAlterTruncateRenameTable() {
        engine.execute("CREATE DATABASE db_alter_test;");
        engine.execute("USE db_alter_test;");
        engine.execute("CREATE TABLE t_alter (id INT PRIMARY KEY, name VARCHAR(50));");

        // Insert some data
        engine.execute("INSERT INTO t_alter VALUES (1, 'John');");

        // Alter table (ADD COLUMN)
        QueryResult r = engine.execute("ALTER TABLE t_alter ADD age INT;");
        assertTrue(r.success);

        // Describe table should show age
        r = engine.execute("DESCRIBE t_alter;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("age", r.rows.get(2).get("Field"));
        assertEquals("INT", r.rows.get(2).get("Type"));

        // Select should return age as NULL for existing row
        r = engine.execute("SELECT age FROM t_alter WHERE id = 1;");
        assertTrue(r.success);
        assertNull(r.rows.get(0).get("age"));

        // Rename table
        r = engine.execute("RENAME TABLE t_alter TO t_renamed;");
        assertTrue(r.success);

        // Old table should not exist
        r = engine.execute("DESCRIBE t_alter;");
        assertFalse(r.success);

        // New table should exist and describe successfully
        r = engine.execute("DESCRIBE t_renamed;");
        assertTrue(r.success);

        // Truncate table
        r = engine.execute("TRUNCATE TABLE t_renamed;");
        assertTrue(r.success);

        // Select should return 0 rows
        r = engine.execute("SELECT * FROM t_renamed;");
        assertTrue(r.success);
        assertEquals(0, r.rows.size());
    }

    @Test
    public void testRevokePrivileges() throws Exception {
        engine.execute("CREATE DATABASE db_revoke_test;");
        engine.execute("USE db_revoke_test;");
        engine.execute("CREATE TABLE t_revoke (id INT PRIMARY KEY);");
        engine.execute("INSERT INTO t_revoke VALUES (1);");

        engine.execute("CREATE USER 'revoke_user'@'localhost' IDENTIFIED BY 'Pass123';");
        engine.execute("GRANT SELECT, INSERT ON db_revoke_test.* TO 'revoke_user'@'localhost';");
        engine.execute("FLUSH PRIVILEGES;");

        // Authenticate as revoke_user
        boolean authed = engine.authenticate("revoke_user", "Pass123");
        assertTrue(authed);

        // Select works
        QueryResult r = engine.execute("SELECT * FROM t_revoke;");
        assertTrue(r.success);

        // Switch back to root
        engine.setCurrentUser("root", "localhost");

        // Revoke SELECT
        r = engine.execute("REVOKE SELECT ON db_revoke_test.* FROM 'revoke_user'@'localhost';");
        assertTrue(r.success);
        engine.execute("FLUSH PRIVILEGES;");

        // Switch back to revoke_user
        engine.setCurrentUser("revoke_user", "localhost");

        // Select should now be denied
        r = engine.execute("SELECT * FROM t_revoke;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Access denied"));

        // Insert should still be allowed
        r = engine.execute("INSERT INTO t_revoke VALUES (2);");
        assertTrue(r.success);

        // Switch back to root
        engine.setCurrentUser("root", "localhost");
    }

    @Test
    public void testTransactionControl() {
        engine.execute("CREATE DATABASE db_tx_test;");
        engine.execute("USE db_tx_test;");
        engine.execute("CREATE TABLE t_tx (id INT PRIMARY KEY, val TEXT);");

        // 1. Rollback test
        QueryResult r = engine.execute("START TRANSACTION;");
        assertTrue(r.success);

        engine.execute("INSERT INTO t_tx VALUES (1, 'A');");
        engine.execute("INSERT INTO t_tx VALUES (2, 'B');");

        // Check inside transaction (data visible)
        r = engine.execute("SELECT * FROM t_tx;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());

        // Rollback
        r = engine.execute("ROLLBACK;");
        assertTrue(r.success);

        // Check after rollback (data gone)
        r = engine.execute("SELECT * FROM t_tx;");
        assertTrue(r.success);
        assertEquals(0, r.rows.size());

        // 2. Commit test
        engine.execute("START TRANSACTION;");
        engine.execute("INSERT INTO t_tx VALUES (3, 'C');");
        r = engine.execute("COMMIT;");
        assertTrue(r.success);

        // Check after commit (data persists)
        r = engine.execute("SELECT * FROM t_tx;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());

        // 3. Savepoint test
        engine.execute("START TRANSACTION;");
        engine.execute("INSERT INTO t_tx VALUES (4, 'D');");
        r = engine.execute("SAVEPOINT my_sp;");
        assertTrue(r.success);

        engine.execute("INSERT INTO t_tx VALUES (5, 'E');");

        // Rollback to savepoint
        r = engine.execute("ROLLBACK TO SAVEPOINT my_sp;");
        assertTrue(r.success);

        // Rollback to savepoint keeps transaction active but discards post-savepoint data
        r = engine.execute("SELECT * FROM t_tx;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size()); // id=3 (committed) and id=4 (before savepoint)

        // Commit transaction
        engine.execute("COMMIT;");

        r = engine.execute("SELECT * FROM t_tx;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
    }

    @Test
    public void testSelectDistinctAndAliases() {
        engine.execute("CREATE DATABASE db_distinct_test;");
        engine.execute("USE db_distinct_test;");
        engine.execute("CREATE TABLE t_distinct (id INT, name VARCHAR(50));");
        engine.execute("INSERT INTO t_distinct VALUES (1, 'Alice');");
        engine.execute("INSERT INTO t_distinct VALUES (2, 'Bob');");
        engine.execute("INSERT INTO t_distinct VALUES (1, 'Alice');"); // duplicate

        // Standard select returns all 3 rows
        QueryResult r = engine.execute("SELECT name FROM t_distinct;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());

        // DISTINCT select returns 2 rows
        r = engine.execute("SELECT DISTINCT name FROM t_distinct;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());

        // AS (alias) select works
        r = engine.execute("SELECT name AS user_name FROM t_distinct;");
        assertTrue(r.success);
        assertEquals("Alice", r.rows.get(0).get("user_name"));
        assertTrue(r.columns.contains("user_name"));
    }

    @Test
    public void testJoins() {
        engine.execute("CREATE DATABASE db_join_test;");
        engine.execute("USE db_join_test;");
        engine.execute("CREATE TABLE users (id INT, name VARCHAR(50));");
        engine.execute("CREATE TABLE orders (id INT, user_id INT, amount DOUBLE);");

        engine.execute("INSERT INTO users VALUES (1, 'Alice');");
        engine.execute("INSERT INTO users VALUES (2, 'Bob');");
        engine.execute("INSERT INTO users VALUES (3, 'Charlie');");

        engine.execute("INSERT INTO orders VALUES (10, 1, 100.5);");
        engine.execute("INSERT INTO orders VALUES (20, 2, 200.0);");
        engine.execute("INSERT INTO orders VALUES (30, 4, 300.0);"); // unmatched user_id

        // 1. INNER JOIN
        QueryResult r = engine.execute("SELECT users.name, orders.amount FROM users INNER JOIN orders ON users.id = orders.user_id;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("Alice", r.rows.get(0).get("users.name"));
        assertEquals(100.5, r.rows.get(0).get("orders.amount"));
        assertEquals("Bob", r.rows.get(1).get("users.name"));
        assertEquals(200.0, r.rows.get(1).get("orders.amount"));

        // 2. LEFT JOIN
        r = engine.execute("SELECT users.name, orders.amount FROM users LEFT JOIN orders ON users.id = orders.user_id ORDER BY users.id ASC;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("Alice", r.rows.get(0).get("users.name"));
        assertEquals(100.5, r.rows.get(0).get("orders.amount"));
        assertEquals("Charlie", r.rows.get(2).get("users.name"));
        assertNull(r.rows.get(2).get("orders.amount"));

        // 3. RIGHT JOIN
        r = engine.execute("SELECT users.name, orders.amount FROM users RIGHT JOIN orders ON users.id = orders.user_id ORDER BY orders.id ASC;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("Alice", r.rows.get(0).get("users.name"));
        assertEquals("Bob", r.rows.get(1).get("users.name"));
        assertNull(r.rows.get(2).get("users.name"));
        assertEquals(300.0, r.rows.get(2).get("orders.amount"));

        // 4. CROSS JOIN
        r = engine.execute("SELECT users.name, orders.amount FROM users CROSS JOIN orders;");
        assertTrue(r.success);
        assertEquals(9, r.rows.size()); // 3 * 3
    }

    @Test
    public void testGroupByAndHaving() {
        engine.execute("CREATE DATABASE db_group_test;");
        engine.execute("USE db_group_test;");
        engine.execute("CREATE TABLE sales (id INT, category VARCHAR(50), amount DOUBLE);");
        engine.execute("INSERT INTO sales VALUES (1, 'Electronics', 100.0);");
        engine.execute("INSERT INTO sales VALUES (2, 'Electronics', 150.0);");
        engine.execute("INSERT INTO sales VALUES (3, 'Clothing', 50.0);");
        engine.execute("INSERT INTO sales VALUES (4, 'Clothing', 70.0);");
        engine.execute("INSERT INTO sales VALUES (5, 'Books', 20.0);");

        // GROUP BY category with aggregates
        QueryResult r = engine.execute("SELECT category, COUNT(*), SUM(amount) FROM sales GROUP BY category ORDER BY category ASC;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());

        // Books
        assertEquals("Books", r.rows.get(0).get("category"));
        assertEquals(1L, r.rows.get(0).get("COUNT(*)"));
        assertEquals(20.0, r.rows.get(0).get("SUM(amount)"));

        // Clothing
        assertEquals("Clothing", r.rows.get(1).get("category"));
        assertEquals(2L, r.rows.get(1).get("COUNT(*)"));
        assertEquals(120.0, r.rows.get(1).get("SUM(amount)"));

        // Electronics
        assertEquals("Electronics", r.rows.get(2).get("category"));
        assertEquals(2L, r.rows.get(2).get("COUNT(*)"));
        assertEquals(250.0, r.rows.get(2).get("SUM(amount)"));

        // HAVING clause
        r = engine.execute("SELECT category, SUM(amount) FROM sales GROUP BY category HAVING SUM(amount) > 100 ORDER BY category ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size()); // Clothing (120) and Electronics (250)
        assertEquals("Clothing", r.rows.get(0).get("category"));
        assertEquals("Electronics", r.rows.get(1).get("category"));
    }

    @Test
    public void testUnion() {
        engine.execute("CREATE DATABASE db_union_test;");
        engine.execute("USE db_union_test;");
        engine.execute("CREATE TABLE t1 (id INT, val TEXT);");
        engine.execute("CREATE TABLE t2 (id INT, val TEXT);");

        engine.execute("INSERT INTO t1 VALUES (1, 'A');");
        engine.execute("INSERT INTO t1 VALUES (2, 'B');");
        engine.execute("INSERT INTO t2 VALUES (2, 'B');"); // duplicate across tables
        engine.execute("INSERT INTO t2 VALUES (3, 'C');");

        // UNION ALL (returns 4 rows)
        QueryResult r = engine.execute("SELECT * FROM t1 UNION ALL SELECT * FROM t2 ORDER BY id ASC;");
        assertTrue(r.message, r.success);
        assertEquals(4, r.rows.size());

        // UNION (returns 3 distinct rows)
        r = engine.execute("SELECT * FROM t1 UNION SELECT * FROM t2 ORDER BY id ASC;");
        assertTrue(r.message, r.success);
        assertEquals(3, r.rows.size());
        assertEquals(1L, r.rows.get(0).get("id"));
        assertEquals(2L, r.rows.get(1).get("id"));
        assertEquals(3L, r.rows.get(2).get("id"));
    }

    @Test
    public void testWhereOperators() {
        engine.execute("CREATE DATABASE db_where_test;");
        engine.execute("USE db_where_test;");
        engine.execute("CREATE TABLE t_where (id INT, val TEXT);");
        engine.execute("INSERT INTO t_where VALUES (1, 'apple');");
        engine.execute("INSERT INTO t_where VALUES (2, 'banana');");
        engine.execute("INSERT INTO t_where VALUES (3, NULL);");
        engine.execute("INSERT INTO t_where VALUES (4, 'cherry');");

        // 1. IN operator
        QueryResult r = engine.execute("SELECT * FROM t_where WHERE val IN ('apple', 'cherry') ORDER BY id ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("apple", r.rows.get(0).get("val"));
        assertEquals("cherry", r.rows.get(1).get("val"));

        // 2. BETWEEN operator
        r = engine.execute("SELECT * FROM t_where WHERE id BETWEEN 2 AND 4 ORDER BY id ASC;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals(2L, r.rows.get(0).get("id"));
        assertEquals(4L, r.rows.get(2).get("id"));

        // 3. IS NULL
        r = engine.execute("SELECT * FROM t_where WHERE val IS NULL;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals(3L, r.rows.get(0).get("id"));

        // 4. IS NOT NULL
        r = engine.execute("SELECT * FROM t_where WHERE val IS NOT NULL ORDER BY id ASC;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
    }

    @Test
    public void testAlterTableColumns() {
        engine.execute("CREATE DATABASE db_alter_cols;");
        engine.execute("USE db_alter_cols;");
        engine.execute("CREATE TABLE t_cols (id INT, name VARCHAR(50));");
        engine.execute("INSERT INTO t_cols VALUES (1, 'Alice');");

        // 1. ADD COLUMN
        QueryResult r = engine.execute("ALTER TABLE t_cols ADD COLUMN age INT;");
        assertTrue(r.message, r.success);
        
        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals(3, r.rows.size());
        assertEquals("age", r.rows.get(2).get("Field"));
        assertEquals("INT", r.rows.get(2).get("Type"));

        // 2. ADD COLUMN FIRST
        r = engine.execute("ALTER TABLE t_cols ADD COLUMN rank INT FIRST;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals(4, r.rows.size());
        assertEquals("rank", r.rows.get(0).get("Field"));

        // 3. ADD COLUMN AFTER
        r = engine.execute("ALTER TABLE t_cols ADD COLUMN middle_name VARCHAR(50) AFTER name;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals(5, r.rows.size());
        assertEquals("middle_name", r.rows.get(3).get("Field")); // rank, id, name, middle_name, age

        // 4. MODIFY COLUMN position and type
        r = engine.execute("ALTER TABLE t_cols MODIFY COLUMN age DOUBLE FIRST;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals("age", r.rows.get(0).get("Field"));
        assertEquals("DOUBLE", r.rows.get(0).get("Type"));

        // 5. CHANGE COLUMN name, type and position
        r = engine.execute("ALTER TABLE t_cols CHANGE COLUMN rank level INT AFTER name;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals("level", r.rows.get(3).get("Field")); // age, id, name, level, middle_name

        // 6. RENAME COLUMN
        r = engine.execute("ALTER TABLE t_cols RENAME COLUMN level TO lv;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals("lv", r.rows.get(3).get("Field"));

        // 7. DROP COLUMN
        r = engine.execute("ALTER TABLE t_cols DROP COLUMN middle_name;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.message, r.success);
        assertEquals(4, r.rows.size());

        // 8. ALTER TABLE ADD COLUMN with UNSIGNED and ON UPDATE
        r = engine.execute("ALTER TABLE t_cols ADD COLUMN price DECIMAL(10,2) UNSIGNED;");
        assertTrue(r.message, r.success);
        
        r = engine.execute("ALTER TABLE t_cols ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;");
        assertTrue(r.message, r.success);

        // Verify UNSIGNED blocks negative price during insert
        r = engine.execute("INSERT INTO t_cols (id, name, price) VALUES (2, 'Bob', -5.00);");
        assertFalse(r.success);
        
        // Success insert
        r = engine.execute("INSERT INTO t_cols (id, name, price) VALUES (2, 'Bob', 12.50);");
        assertTrue(r.success);

        // Fetch row 2 to record updated_at timestamp
        r = engine.execute("SELECT * FROM t_cols WHERE id = 2;");
        assertTrue(r.success);
        String oldTs = (String) r.rows.get(0).get("updated_at");

        // Wait, then update
        try { Thread.sleep(1100); } catch (Exception e) {}
        
        // Run update that changes a column
        r = engine.execute("UPDATE t_cols SET name = 'Bobby' WHERE id = 2;");
        assertTrue(r.success);

        // Select and assert that updated_at changed
        r = engine.execute("SELECT * FROM t_cols WHERE id = 2;");
        assertTrue(r.success);
        String newTs = (String) r.rows.get(0).get("updated_at");
        assertNotNull(newTs);
        assertNotEquals(oldTs, newTs);

        // Update to negative price (fails)
        r = engine.execute("UPDATE t_cols SET price = -10.00 WHERE id = 2;");
        assertFalse(r.success);

        // 9. ALTER COLUMN ... SET ON UPDATE CURRENT_TIMESTAMP
        // Let's create a new column 'modified_time' without default or on update
        r = engine.execute("ALTER TABLE t_cols ADD COLUMN modified_time TIMESTAMP;");
        assertTrue(r.success);

        // Describe and verify modified_time extra info is empty
        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.success);
        // Find modified_time column
        Map<String, Object> modTimeCol = null;
        for (Map<String, Object> colRow : r.rows) {
            if ("modified_time".equalsIgnoreCase((String) colRow.get("Field"))) {
                modTimeCol = colRow;
                break;
            }
        }
        assertNotNull(modTimeCol);
        assertEquals("", modTimeCol.get("Extra"));

        // Now set ON UPDATE CURRENT_TIMESTAMP on modified_time
        r = engine.execute("ALTER TABLE t_cols ALTER COLUMN modified_time SET ON UPDATE CURRENT_TIMESTAMP;");
        assertTrue(r.success);

        // Describe and verify Extra now says "on update CURRENT_TIMESTAMP"
        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.success);
        modTimeCol = null;
        for (Map<String, Object> colRow : r.rows) {
            if ("modified_time".equalsIgnoreCase((String) colRow.get("Field"))) {
                modTimeCol = colRow;
                break;
            }
        }
        assertNotNull(modTimeCol);
        assertEquals("on update CURRENT_TIMESTAMP", modTimeCol.get("Extra"));

        // Let's update and verify modified_time is auto-updated
        r = engine.execute("SELECT * FROM t_cols WHERE id = 2;");
        assertTrue(r.success);
        String oldModTime = (String) r.rows.get(0).get("modified_time");
        
        try { Thread.sleep(1100); } catch (Exception e) {}

        r = engine.execute("UPDATE t_cols SET name = 'Robert' WHERE id = 2;");
        assertTrue(r.success);

        r = engine.execute("SELECT * FROM t_cols WHERE id = 2;");
        assertTrue(r.success);
        String newModTime = (String) r.rows.get(0).get("modified_time");
        assertNotNull(newModTime);
        assertNotEquals(oldModTime, newModTime);

        // 10. ALTER COLUMN ... DROP ON UPDATE
        r = engine.execute("ALTER TABLE t_cols ALTER COLUMN modified_time DROP ON UPDATE;");
        assertTrue(r.success);

        // Verify Extra is now empty
        r = engine.execute("DESCRIBE t_cols;");
        assertTrue(r.success);
        modTimeCol = null;
        for (Map<String, Object> colRow : r.rows) {
            if ("modified_time".equalsIgnoreCase((String) colRow.get("Field"))) {
                modTimeCol = colRow;
                break;
            }
        }
        assertNotNull(modTimeCol);
        assertEquals("", modTimeCol.get("Extra"));

        // Verify updates no longer change modified_time
        String modTimeAfterDrop = newModTime;
        
        try { Thread.sleep(1100); } catch (Exception e) {}

        r = engine.execute("UPDATE t_cols SET name = 'Rob' WHERE id = 2;");
        assertTrue(r.success);

        r = engine.execute("SELECT * FROM t_cols WHERE id = 2;");
        assertTrue(r.success);
        String finalModTime = (String) r.rows.get(0).get("modified_time");
        assertEquals(modTimeAfterDrop, finalModTime);
    }

    @Test
    public void testAlterTableKeys() {
        engine.execute("CREATE DATABASE db_alter_keys;");
        engine.execute("USE db_alter_keys;");
        
        // Setup table
        engine.execute("CREATE TABLE users (id INT, email VARCHAR(100));");
        engine.execute("CREATE TABLE orders (order_id INT, user_id INT);");

        // 1. ADD PRIMARY KEY
        QueryResult r = engine.execute("ALTER TABLE users ADD PRIMARY KEY (id);");
        assertTrue(r.message, r.success);
        
        r = engine.execute("DESCRIBE users;");
        assertTrue(r.message, r.success);
        assertEquals("PRI", r.rows.get(0).get("Key"));

        // 2. DROP PRIMARY KEY
        r = engine.execute("ALTER TABLE users DROP PRIMARY KEY;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE users;");
        assertTrue(r.message, r.success);
        assertEquals("", r.rows.get(0).get("Key"));

        // 3. ADD UNIQUE KEY
        r = engine.execute("ALTER TABLE users ADD UNIQUE KEY email_uniq (email);");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE users;");
        assertTrue(r.message, r.success);
        assertEquals("UNI", r.rows.get(1).get("Key"));

        // 4. DROP INDEX
        r = engine.execute("ALTER TABLE users DROP INDEX email_uniq;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE users;");
        assertTrue(r.message, r.success);
        assertEquals("", r.rows.get(1).get("Key"));

        // Test ADD CONSTRAINT UNIQUE
        r = engine.execute("ALTER TABLE users ADD CONSTRAINT uc_email UNIQUE (email);");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE users;");
        assertTrue(r.message, r.success);
        assertEquals("UNI", r.rows.get(1).get("Key"));

        // DROP INDEX uc_email
        r = engine.execute("ALTER TABLE users DROP INDEX uc_email;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE users;");
        assertTrue(r.message, r.success);
        assertEquals("", r.rows.get(1).get("Key"));

        // 5. ADD FOREIGN KEY
        r = engine.execute("ALTER TABLE orders ADD FOREIGN KEY (user_id) REFERENCES users(id);");
        assertTrue(r.message, r.success);

        // We can test if violating foreign key insertion fails
        engine.execute("INSERT INTO users VALUES (1, 'alice@example.com');");
        
        // This should fail since parent id 2 does not exist
        r = engine.execute("INSERT INTO orders VALUES (101, 2);");
        assertFalse(r.message, r.success);

        // This should succeed
        r = engine.execute("INSERT INTO orders VALUES (101, 1);");
        assertTrue(r.message, r.success);

        // 6. DROP FOREIGN KEY
        r = engine.execute("ALTER TABLE orders DROP FOREIGN KEY user_id;");
        assertTrue(r.message, r.success);

        // Now inserting parent id 2 should succeed
        r = engine.execute("INSERT INTO orders VALUES (102, 2);");
        assertTrue(r.message, r.success);
    }

    @Test
    public void testAlterTableConstraints() {
        engine.execute("CREATE DATABASE db_alter_constraints;");
        engine.execute("USE db_alter_constraints;");
        engine.execute("CREATE TABLE items (id INT, price INT DEFAULT 10);");

        // 1. DEFAULT VALUE CHANGE SET
        QueryResult r = engine.execute("ALTER TABLE items ALTER price SET DEFAULT 20;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE items;");
        assertTrue(r.message, r.success);
        assertEquals("20", r.rows.get(1).get("Default"));

        // 2. DEFAULT VALUE CHANGE DROP
        r = engine.execute("ALTER TABLE items ALTER price DROP DEFAULT;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE items;");
        assertTrue(r.message, r.success);
        assertNull(r.rows.get(1).get("Default"));

        // 3. ADD CHECK CONSTRAINT
        r = engine.execute("ALTER TABLE items ADD CHECK (price > 0);");
        assertTrue(r.message, r.success);

        // Violating check should fail
        r = engine.execute("INSERT INTO items VALUES (1, 0);");
        assertFalse(r.message, r.success);

        // Valid should pass
        r = engine.execute("INSERT INTO items VALUES (1, 5);");
        assertTrue(r.message, r.success);

        // 4. DROP CHECK CONSTRAINT
        r = engine.execute("ALTER TABLE items DROP CHECK items_chk_1;");
        assertTrue(r.message, r.success);

        // Now violating check should pass
        r = engine.execute("INSERT INTO items VALUES (2, 0);");
        assertTrue(r.message, r.success);
    }

    @Test
    public void testAlterSettings() {
        engine.execute("CREATE DATABASE db_alter_settings;");
        engine.execute("USE db_alter_settings;");
        engine.execute("CREATE TABLE t_settings (id INT);");

        // 1. ENGINE CHANGE
        QueryResult r = engine.execute("ALTER TABLE t_settings ENGINE = InnoDB;");
        assertTrue(r.message, r.success);

        // 2. CHARACTER SET CHANGE
        r = engine.execute("ALTER TABLE t_settings CHARACTER SET utf8mb4;");
        assertTrue(r.message, r.success);

        // 3. RENAME TABLE
        r = engine.execute("ALTER TABLE t_settings RENAME TO t_new;");
        assertTrue(r.message, r.success);

        r = engine.execute("DESCRIBE t_settings;");
        assertFalse(r.message, r.success);

        r = engine.execute("DESCRIBE t_new;");
        assertTrue(r.message, r.success);
    }

    @Test
    public void testConcatFunction() {
        engine.execute("CREATE DATABASE db_concat_test;");
        engine.execute("USE db_concat_test;");
        engine.execute("CREATE TABLE t_concat (val TEXT);");
        engine.execute("INSERT INTO t_concat VALUES ('A');");

        // 1. Literal concatenation
        QueryResult r = engine.execute("SELECT CONCAT('Hello', ' ', 'World') AS res FROM t_concat;");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());
        assertEquals("Hello World", r.rows.get(0).get("res"));

        // 2. Mixed literal and column concatenation
        r = engine.execute("SELECT CONCAT(val, 'B', 'C') AS res FROM t_concat;");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());
        assertEquals("ABC", r.rows.get(0).get("res"));
    }

    @Test
    public void testSuggestionsMetadata() {
        // Initially empty
        assertTrue(engine.getTablesList().isEmpty());

        engine.execute("CREATE DATABASE db_suggestions;");
        engine.execute("USE db_suggestions;");

        // Still empty after database creation but no tables
        assertTrue(engine.getTablesList().isEmpty());

        // Create table
        engine.execute("CREATE TABLE users (id INT, username TEXT, score DOUBLE);");
        List<String> tables = engine.getTablesList();
        assertEquals(1, tables.size());
        assertEquals("users", tables.get(0));

        // Get columns
        List<String> cols = engine.getColumnsList("users");
        assertEquals(3, cols.size());
        assertTrue(cols.contains("id"));
        assertTrue(cols.contains("username"));
        assertTrue(cols.contains("score"));

        // Get columns for non-existent table
        assertTrue(engine.getColumnsList("nonexistent").isEmpty());
    }

    @Test
    public void testUserDefinedFunctions() {
        engine.execute("CREATE DATABASE db_udf;");
        engine.execute("USE db_udf;");

        // 1. Create simple UDF
        QueryResult r = engine.execute(
            "CREATE FUNCTION add_numbers(a INT, b INT) " +
            "RETURNS INT " +
            "BEGIN " +
            "    RETURN a + b; " +
            "END"
        );
        if (!r.success) {
            System.out.println("DEBUG UDF CREATE FAILURE: " + r.message);
        }
        assertTrue(r.message, r.success);

        // Evaluate simple UDF
        r = engine.execute("SELECT add_numbers(15, 25) AS result;");
        if (!r.success) {
            System.out.println("DEBUG UDF SELECT FAILURE: " + r.message);
        }
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());
        assertEquals(40.0, SqlFunctions.parseDouble(r.rows.get(0).get("result")), 0.001);

        // 2. Create conditional UDF
        r = engine.execute(
            "CREATE FUNCTION is_adult(age INT) " +
            "RETURNS VARCHAR(20) " +
            "BEGIN " +
            "    IF age >= 18 THEN " +
            "        RETURN 'Adult'; " +
            "    ELSE " +
            "        RETURN 'Minor'; " +
            "    END IF; " +
            "END"
        );
        assertTrue(r.message, r.success);

        // Evaluate conditional UDF (Adult)
        r = engine.execute("SELECT is_adult(22) AS res;");
        assertTrue(r.message, r.success);
        assertEquals("Adult", r.rows.get(0).get("res"));

        // Evaluate conditional UDF (Minor)
        r = engine.execute("SELECT is_adult(15) AS res;");
        assertTrue(r.message, r.success);
        assertEquals("Minor", r.rows.get(0).get("res"));

        // 3. Create complex variables assignment UDF
        r = engine.execute(
            "CREATE FUNCTION calculate_discount(price DOUBLE, pct INT) " +
            "RETURNS DOUBLE " +
            "BEGIN " +
            "    DECLARE final DOUBLE; " +
            "    SET final = price - (price * pct / 100); " +
            "    RETURN final; " +
            "END"
        );
        assertTrue(r.message, r.success);

        // Evaluate complex variables assignment UDF
        r = engine.execute("SELECT calculate_discount(120, 15) AS discounted;");
        assertTrue(r.message, r.success);
        assertEquals(102.0, SqlFunctions.parseDouble(r.rows.get(0).get("discounted")), 0.001);

        // 4. Show functions
        r = engine.execute("SHOW FUNCTION STATUS;");
        assertTrue(r.message, r.success);
        assertEquals(3, r.rows.size());
        
        // Assert function names are listed
        boolean hasAdd = false;
        boolean hasAdult = false;
        boolean hasDisc = false;
        for (Map<String, Object> row : r.rows) {
            String name = (String) row.get("Name");
            if ("add_numbers".equalsIgnoreCase(name)) hasAdd = true;
            if ("is_adult".equalsIgnoreCase(name)) hasAdult = true;
            if ("calculate_discount".equalsIgnoreCase(name)) hasDisc = true;
        }
        assertTrue(hasAdd);
        assertTrue(hasAdult);
        assertTrue(hasDisc);

        // 5. Drop function
        r = engine.execute("DROP FUNCTION add_numbers;");
        assertTrue(r.message, r.success);

        r = engine.execute("SHOW FUNCTION STATUS;");
        assertTrue(r.message, r.success);
        assertEquals(2, r.rows.size());
    }

    @Test
    public void testCreateTableWithConstraint() {
        engine.execute("CREATE DATABASE db_constraint_test;");
        engine.execute("USE db_constraint_test;");
        
        QueryResult r = engine.execute("CREATE TABLE users (\n" +
            "    id              INT AUTO_INCREMENT PRIMARY KEY,\n" +
            "    full_name       VARCHAR(100)  NOT NULL,\n" +
            "    email           VARCHAR(150)  NOT NULL,\n" +
            "    phone           VARCHAR(20)   DEFAULT NULL,\n" +
            "    password_hash   VARCHAR(255)  NOT NULL,\n" +
            "    gender          ENUM('male','female','other') DEFAULT NULL,\n" +
            "    date_of_birth   DATE          DEFAULT NULL,\n" +
            "    profile_image   VARCHAR(255)  DEFAULT NULL,\n" +
            "    status          ENUM('active','inactive','banned') NOT NULL DEFAULT 'active',\n" +
            "    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
            "    CONSTRAINT uq_users_email UNIQUE (email)\n" +
            ");");
        assertTrue(r.message, r.success);

        // Verify we can insert a row
        r = engine.execute("INSERT INTO users (full_name, email, password_hash) VALUES ('John Doe', 'john@example.com', 'hash123');");
        assertTrue(r.message, r.success);

        // Verify unique constraint uq_users_email is active and prevents duplicate email insertion
        r = engine.execute("INSERT INTO users (full_name, email, password_hash) VALUES ('Jane Doe', 'john@example.com', 'hash456');");
        assertFalse(r.message, r.success);
        assertTrue(r.message, r.message.contains("UNIQUE constraint failed") || r.message.contains("Duplicate entry") || r.message.contains("already exists"));
    }

    @Test
    public void testCreateDatabaseWithOptions() throws Exception {
        QueryResult r = engine.execute("CREATE DATABASE IF NOT EXISTS college\n" +
            "    CHARACTER SET utf8mb4\n" +
            "    COLLATE utf8mb4_unicode_ci;");
        assertTrue(r.message, r.success);

        // Verify show create database returns the expected sql
        r = engine.execute("SHOW CREATE DATABASE college;");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());
        String createSql = (String) r.rows.get(0).get("Create Database");
        assertTrue(createSql, createSql.contains("DEFAULT CHARACTER SET utf8mb4"));
        assertTrue(createSql, createSql.contains("COLLATE utf8mb4_unicode_ci"));

        // Test Alter Database
        r = engine.execute("ALTER DATABASE college CHARACTER SET latin1 COLLATE latin1_bin;");
        assertTrue(r.message, r.success);

        r = engine.execute("SHOW CREATE DATABASE college;");
        createSql = (String) r.rows.get(0).get("Create Database");
        assertTrue(createSql, createSql.contains("DEFAULT CHARACTER SET latin1"));
        assertTrue(createSql, createSql.contains("COLLATE latin1_bin"));
    }

    @Test
    public void testCreateTableWithOptions() throws Exception {
        engine.execute("CREATE DATABASE db_table_options;");
        engine.execute("USE db_table_options;");
        QueryResult r = engine.execute("CREATE TABLE users_opt (\n" +
            "    id INT PRIMARY KEY,\n" +
            "    name VARCHAR(100) COLLATE utf8mb4_bin\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1 COMMENT='Users Table';");
        assertTrue(r.message, r.success);

        // Test SHOW CREATE TABLE
        r = engine.execute("SHOW CREATE TABLE users_opt;");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());
        String createSql = (String) r.rows.get(0).get("Create Table");
        assertTrue(createSql, createSql.contains("users_opt"));
        assertTrue(createSql, createSql.contains("DEFAULT CHARSET=utf8mb4"));
        assertTrue(createSql, createSql.contains("COLLATE=utf8mb4_unicode_ci"));
        assertTrue(createSql, createSql.contains("COLLATE utf8mb4_bin"));

        // Test SHOW CHARACTER SET
        r = engine.execute("SHOW CHARACTER SET;");
        assertTrue(r.message, r.success);
        assertTrue(r.rows.size() > 0);
        assertTrue(r.columns.contains("Charset"));

        // Test SHOW COLLATION
        r = engine.execute("SHOW COLLATION;");
        assertTrue(r.message, r.success);
        assertTrue(r.rows.size() > 0);
        assertTrue(r.columns.contains("Collation"));
    }

    @Test
    public void testCollationAwareComparisons() throws Exception {
        engine.execute("CREATE DATABASE db_collation_comp;");
        engine.execute("USE db_collation_comp;");

        // 1. Case Insensitive Table
        engine.execute("CREATE TABLE users_ci (id INT, name VARCHAR(100) COLLATE utf8mb4_general_ci UNIQUE);");
        engine.execute("INSERT INTO users_ci VALUES (1, 'Alice');");
        
        // Uniqueness check should prevent duplicate case-insensitively
        QueryResult r = engine.execute("INSERT INTO users_ci VALUES (2, 'alice');");
        assertFalse(r.message, r.success);
        assertTrue(r.message.contains("Duplicate entry") || r.message.contains("already exists"));

        // Select query where check case-insensitively
        r = engine.execute("SELECT * FROM users_ci WHERE name = 'aLiCe';");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());

        // 2. Binary Case Sensitive Table
        engine.execute("CREATE TABLE users_bin (id INT, name VARCHAR(100) COLLATE utf8mb4_bin UNIQUE);");
        engine.execute("INSERT INTO users_bin VALUES (1, 'Alice');");
        
        // Uniqueness check should allow duplicate with different case
        r = engine.execute("INSERT INTO users_bin VALUES (2, 'alice');");
        assertTrue(r.message, r.success);

        // Select query where check case-sensitively
        r = engine.execute("SELECT * FROM users_bin WHERE name = 'aLiCe';");
        assertTrue(r.message, r.success);
        assertEquals(0, r.rows.size());

        // 3. Accent Insensitive Table
        engine.execute("CREATE TABLE users_ai (id INT, name VARCHAR(100) COLLATE utf8mb4_0900_ai_ci);");
        engine.execute("INSERT INTO users_ai VALUES (1, 'Café');");
        
        r = engine.execute("SELECT * FROM users_ai WHERE name = 'cafe';");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size()); // Café should match cafe

        // 4. Alter Table Convert To
        engine.execute("CREATE TABLE convert_test (name VARCHAR(50) COLLATE utf8mb4_general_ci);");
        engine.execute("INSERT INTO convert_test VALUES ('Test');");
        
        r = engine.execute("ALTER TABLE convert_test CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;");
        assertTrue(r.message, r.success);

        // Verify the conversion applied to show create table
        r = engine.execute("SHOW CREATE TABLE convert_test;");
        String createSql = (String) r.rows.get(0).get("Create Table");
        assertTrue(createSql, createSql.contains("COLLATE utf8mb4_bin"));
    }

    @Test
    public void testSetStatement() {
        engine.execute("CREATE DATABASE db_set_test;");
        engine.execute("USE db_set_test;");
        
        engine.execute("CREATE TABLE parent (id INT PRIMARY KEY);");
        engine.execute("CREATE TABLE child (id INT PRIMARY KEY, parent_id INT, FOREIGN KEY (parent_id) REFERENCES parent(id));");
        
        // 1. With FOREIGN_KEY_CHECKS = 1 (default), inserting non-existent reference fails
        QueryResult r = engine.execute("INSERT INTO child VALUES (1, 99);");
        assertFalse(r.success);
        
        // 2. Disable FOREIGN_KEY_CHECKS
        r = engine.execute("SET FOREIGN_KEY_CHECKS = 0;");
        assertTrue(r.message, r.success);
        
        // Now inserting non-existent reference succeeds
        r = engine.execute("INSERT INTO child VALUES (1, 99);");
        assertTrue(r.message, r.success);
        
        // 3. Re-enable FOREIGN_KEY_CHECKS
        r = engine.execute("SET FOREIGN_KEY_CHECKS = 1;");
        assertTrue(r.message, r.success);
        
        // Inserting another invalid reference fails
        r = engine.execute("INSERT INTO child VALUES (2, 999);");
        assertFalse(r.success);
        
        // 4. Test other set commands
        r = engine.execute("SET NAMES utf8mb4;");
        assertTrue(r.success);
        
        r = engine.execute("SET sql_safe_updates = 0;");
        assertTrue(r.success);
    }

    @Test
    public void testColumnAttributes() throws Exception {
        engine.execute("CREATE DATABASE db_attrs_test;");
        engine.execute("USE db_attrs_test;");

        // 1. Test UNSIGNED
        engine.execute("CREATE TABLE t_unsigned (val INT UNSIGNED);");
        QueryResult r = engine.execute("INSERT INTO t_unsigned VALUES (10);");
        assertTrue(r.success);
        r = engine.execute("INSERT INTO t_unsigned VALUES (-5);");
        assertFalse(r.success); // Should fail due to negative value in UNSIGNED column
        assertTrue(r.message.contains("UNSIGNED"));

        // 2. Test ZEROFILL
        engine.execute("CREATE TABLE t_zerofill (val INT(5) ZEROFILL);");
        engine.execute("INSERT INTO t_zerofill VALUES (12);");
        r = engine.execute("SELECT val FROM t_zerofill;");
        assertTrue(r.success);
        assertEquals("00012", r.rows.get(0).get("val").toString());

        // 3. Test GENERATED ALWAYS AS
        engine.execute("CREATE TABLE t_generated (id INT, price DOUBLE, qty INT, total DOUBLE GENERATED ALWAYS AS (price * qty) STORED);");
        r = engine.execute("INSERT INTO t_generated (id, price, qty) VALUES (1, 12.5, 4);");
        assertTrue(r.success);
        r = engine.execute("SELECT total FROM t_generated;");
        assertTrue(r.success);
        assertEquals(50.0, ((Number) r.rows.get(0).get("total")).doubleValue(), 0.001);

        engine.execute("UPDATE t_generated SET price = 10.0 WHERE id = 1;");
        r = engine.execute("SELECT total FROM t_generated;");
        assertTrue(r.success);
        assertEquals(40.0, ((Number) r.rows.get(0).get("total")).doubleValue(), 0.001);

        // 4. Test INVISIBLE
        engine.execute("CREATE TABLE t_invisible (id INT, secret TEXT INVISIBLE);");
        engine.execute("INSERT INTO t_invisible VALUES (1, 'my-secret-key');");
        
        // SELECT * should not return secret
        r = engine.execute("SELECT * FROM t_invisible;");
        assertTrue(r.success);
        assertFalse(r.columns.contains("secret"));
        
        // Explicit SELECT should return secret
        r = engine.execute("SELECT id, secret FROM t_invisible;");
        assertTrue(r.success);
        assertTrue(r.columns.contains("secret"));
        assertEquals("my-secret-key", r.rows.get(0).get("secret"));

        // 5. Test BINARY attribute (collation hook)
        engine.execute("CREATE TABLE t_binary (name VARCHAR(50) BINARY UNIQUE);");
        r = engine.execute("INSERT INTO t_binary VALUES ('Alice');");
        assertTrue(r.success);
        
        // Under BINARY (case sensitive), 'alice' should be allowed as unique, not treated as duplicate of 'Alice'
        r = engine.execute("INSERT INTO t_binary VALUES ('alice');");
        assertTrue(r.success);

        // Under BINARY, WHERE clause should be case-sensitive
        r = engine.execute("SELECT * FROM t_binary WHERE name = 'alice';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("alice", r.rows.get(0).get("name"));

        // 6. Test Column-level REFERENCES (Foreign Keys)
        engine.execute("CREATE TABLE t_parent (id INT PRIMARY KEY);");
        engine.execute("CREATE TABLE t_child (id INT PRIMARY KEY, parent_id INT REFERENCES t_parent(id));");
        engine.execute("INSERT INTO t_parent VALUES (1);");

        // Violating reference check should fail
        r = engine.execute("INSERT INTO t_child VALUES (10, 99);");
        assertFalse(r.success);

        // Correct reference check should succeed
        r = engine.execute("INSERT INTO t_child VALUES (10, 1);");
        assertTrue(r.success);

        // 7. Test Table-level FOREIGN KEY with ON DELETE / UPDATE actions
        engine.execute("CREATE TABLE t_parent2 (id INT PRIMARY KEY);");
        r = engine.execute("CREATE TABLE t_child2 (id INT PRIMARY KEY, parent_id INT, CONSTRAINT fk_parent2 FOREIGN KEY (parent_id) REFERENCES t_parent2(id) ON DELETE CASCADE ON UPDATE NO ACTION);");
        assertTrue(r.success);

        // 8. Test CREATE INDEX / CREATE UNIQUE INDEX
        r = engine.execute("CREATE INDEX idx_parent_id ON t_child2(parent_id);");
        assertTrue(r.success);
        
        r = engine.execute("CREATE UNIQUE INDEX idx_parent_unique ON t_child2(id);");
        assertTrue(r.success);
    }

    @Test
    public void testSqlScriptRunner() throws Exception {
        // Locate schema and seed files for the ecommerce database
        File schemaFile = null;
        File seedFile = null;

        String[] basePaths = {"app/src/main/assets/databases/ecommerce", "src/main/assets/databases/ecommerce"};
        for (String base : basePaths) {
            File s = new File(base + "/schema.sql");
            File d = new File(base + "/seed.sql");
            if (s.exists() && d.exists()) {
                schemaFile = s;
                seedFile = d;
                break;
            }
        }

        assertNotNull("Schema file should exist", schemaFile);
        assertNotNull("Seed file should exist", seedFile);
        
        try {
            System.err.println("DEBUG SHA2 PARSE: " + SqlFunctions.parse("SHA2('pass1234', 256)"));
            System.err.println("DEBUG SHA2 EVALUATE: " + SqlFunctions.evaluate("SHA2('pass1234', 256)", null, engine));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Run schema first, then seed data
        try (java.io.FileInputStream fis = new java.io.FileInputStream(schemaFile)) {
            SqlScriptRunner.runScript(engine, fis);
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(seedFile)) {
            SqlScriptRunner.runScript(engine, fis, "ecommerce");
        }
        
        assertEquals("ecommerce", engine.getActiveDatabase());
        
        QueryResult r = engine.execute("SHOW TABLES;");
        assertTrue(r.success);
        
        boolean hasUsers = false;
        boolean hasProducts = false;
        for (Map<String, Object> row : r.rows) {
            String tableName = null;
            for (String key : row.keySet()) {
                if (key.toLowerCase().startsWith("tables_in_")) {
                    tableName = (String) row.get(key);
                    break;
                }
            }
            if (tableName == null && !row.isEmpty()) {
                tableName = (String) row.values().iterator().next();
            }
            if ("users".equalsIgnoreCase(tableName)) {
                hasUsers = true;
            } else if ("products".equalsIgnoreCase(tableName)) {
                hasProducts = true;
            }
        }
        assertTrue("Should have created 'users' table", hasUsers);
        assertTrue("Should have created 'products' table", hasProducts);
        
        QueryResult selectUsers = engine.execute("SELECT COUNT(*) FROM users;");
        assertTrue(selectUsers.success);
        Number count = (Number) selectUsers.rows.get(0).values().iterator().next();
        assertTrue("Should have users loaded", count.intValue() > 0);

        // Verify dropping and recreating works correctly
        QueryResult dropDb = engine.execute("DROP DATABASE ecommerce;");
        assertTrue(dropDb.success);

        // Verify it is dropped
        QueryResult showDbs = engine.execute("SHOW DATABASES;");
        assertTrue(showDbs.success);
        boolean hasEcommerce = false;
        for (Map<String, Object> row : showDbs.rows) {
            if ("ecommerce".equalsIgnoreCase((String) row.get("Database"))) {
                hasEcommerce = true;
            }
        }
        assertFalse("Database 'ecommerce' should be dropped", hasEcommerce);

        // Re-run script from files
        try (java.io.FileInputStream fis = new java.io.FileInputStream(schemaFile)) {
            SqlScriptRunner.runScript(engine, fis);
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(seedFile)) {
            SqlScriptRunner.runScript(engine, fis, "ecommerce");
        }

        assertEquals("ecommerce", engine.getActiveDatabase());
        QueryResult selectUsers2 = engine.execute("SELECT COUNT(*) FROM users;");
        assertTrue(selectUsers2.success);
        Number count2 = (Number) selectUsers2.rows.get(0).values().iterator().next();
        assertTrue("Should have users loaded after recreating database", count2.intValue() > 0);
    }

    @Test
    public void testViewsAndProcedures() throws Exception {
        engine.execute("CREATE DATABASE test_vp;");
        engine.execute("USE test_vp;");
        engine.execute("CREATE TABLE products (id INT, name VARCHAR(100), stock INT);");
        engine.execute("INSERT INTO products VALUES (1, 'Apple', 5), (2, 'Banana', 20), (3, 'Orange', 2);");

        // 1. Create View
        QueryResult r = engine.execute("CREATE VIEW v_low_stock AS SELECT name, stock FROM products WHERE stock < 10;");
        assertTrue(r.success);

        // 2. Select from View
        r = engine.execute("SELECT * FROM v_low_stock ORDER BY stock ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("Orange", r.rows.get(0).get("name"));
        assertEquals(2L, r.rows.get(0).get("stock"));
        assertEquals("Apple", r.rows.get(1).get("name"));
        assertEquals(5L, r.rows.get(1).get("stock"));

        // 3. SHOW FULL TABLES
        r = engine.execute("SHOW FULL TABLES WHERE TABLE_TYPE = 'VIEW';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        String colName = "Tables_in_test_vp";
        assertEquals("v_low_stock", r.rows.get(0).get(colName));
        assertEquals("VIEW", r.rows.get(0).get("Table_type"));

        r = engine.execute("SHOW FULL TABLES;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size()); // products, v_low_stock

        // 4. Describe View
        r = engine.execute("DESCRIBE v_low_stock;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("name", r.rows.get(0).get("Field"));
        assertEquals("stock", r.rows.get(1).get("Field"));

        // 5. Create Stored Procedure
        r = engine.execute("CREATE PROCEDURE sp_test_proc() BEGIN SELECT * FROM products; END;");
        assertTrue(r.success);

        // 6. SHOW PROCEDURE STATUS
        r = engine.execute("SHOW PROCEDURE STATUS WHERE Db = 'test_vp';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("sp_test_proc", r.rows.get(0).get("Name"));
        assertEquals("test_vp", r.rows.get(0).get("Db"));

        // 7. SHOW CREATE PROCEDURE
        r = engine.execute("SHOW CREATE PROCEDURE sp_test_proc;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("sp_test_proc", r.rows.get(0).get("Procedure"));
        assertTrue(((String) r.rows.get(0).get("Create Procedure")).contains("sp_test_proc"));

        // 8. Triggers & Events DDL
        r = engine.execute("CREATE TRIGGER trig_test AFTER INSERT ON products FOR EACH ROW BEGIN END;");
        assertTrue(r.success);

        r = engine.execute("CREATE EVENT ev_test ON SCHEDULE EVERY 1 HOUR DO BEGIN END;");
        assertTrue(r.success);

        // 8b. User view query testing with table aliases and joins
        engine.execute("CREATE TABLE inventory (product_id INT, available_stock INT, reorder_level INT, warehouse_location TEXT);");
        engine.execute("INSERT INTO inventory VALUES (1, 3, 5, 'Aisle 1'), (2, 10, 5, 'Aisle 2');");

        r = engine.execute("CREATE OR REPLACE VIEW v_low_stock_products AS " +
                           "SELECT p.id, p.name, i.available_stock, i.reorder_level, i.warehouse_location " +
                           "FROM inventory i " +
                           "JOIN products p ON p.id = i.product_id " +
                           "WHERE i.available_stock <= i.reorder_level " +
                           "ORDER BY i.available_stock ASC;");
        assertTrue(r.success);

        r = engine.execute("SELECT * FROM v_low_stock_products;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals(1L, r.rows.get(0).get("id"));
        assertEquals("Apple", r.rows.get(0).get("name"));
        assertEquals(3L, r.rows.get(0).get("available_stock"));

        r = engine.execute("DROP VIEW v_low_stock_products;");
        assertTrue(r.success);
        engine.execute("DROP TABLE inventory;");

        // 9. Drop Table error on View
        r = engine.execute("DROP TABLE v_low_stock;");
        assertFalse(r.success);
        assertTrue(r.message.contains("is a VIEW"));

        // 10. Drop View error on Table
        r = engine.execute("DROP VIEW products;");
        assertFalse(r.success);
        assertTrue(r.message.contains("is not a VIEW"));

        // 11. Drops
        r = engine.execute("DROP VIEW v_low_stock;");
        assertTrue(r.success);

        r = engine.execute("DROP PROCEDURE sp_test_proc;");
        assertTrue(r.success);

        // Create mock tables for sp_get_order_invoice and sp_place_order validation
        engine.execute("CREATE TABLE users (id INT, full_name VARCHAR(100), email VARCHAR(100));");
        engine.execute("CREATE TABLE user_addresses (id INT, address_line VARCHAR(100), city VARCHAR(50), state VARCHAR(50), country VARCHAR(50), postal_code VARCHAR(20));");
        engine.execute("ALTER TABLE products ADD COLUMN sku VARCHAR(50);");
        engine.execute("CREATE TABLE orders (id INT, user_id INT, shipping_address_id INT, billing_address_id INT, order_number VARCHAR(50), placed_at DATETIME, order_status VARCHAR(50), payment_status VARCHAR(50), subtotal DECIMAL(10,2), tax_amount DECIMAL(10,2), shipping_charge DECIMAL(10,2), discount_amount DECIMAL(10,2), grand_total DECIMAL(10,2));");
        engine.execute("CREATE TABLE order_items (order_id INT, product_id INT, quantity INT, unit_price DECIMAL(10,2), discount DECIMAL(10,2), total_price DECIMAL(10,2));");
        engine.execute("CREATE TABLE cart_items (cart_id INT, product_id INT, quantity INT, price DECIMAL(10,2));");
        engine.execute("CREATE TABLE carts (id INT, user_id INT);");
        engine.execute("CREATE TABLE coupons (id INT, code VARCHAR(50), status VARCHAR(50), expires_at DATETIME, minimum_order_amount DECIMAL(10,2), discount_type VARCHAR(50), discount_value DECIMAL(10,2), max_discount DECIMAL(10,2));");

        // Verify user's actual stored procedures parse and persist successfully
        String sqlGetOrderInvoice = "CREATE PROCEDURE sp_get_order_invoice(IN p_order_id INT UNSIGNED)\n" +
            "BEGIN\n" +
            "    SELECT\n" +
            "        o.order_number,\n" +
            "        o.placed_at,\n" +
            "        o.order_status,\n" +
            "        o.payment_status,\n" +
            "        u.full_name  AS customer_name,\n" +
            "        u.email      AS customer_email,\n" +
            "        ua.address_line, ua.city, ua.state, ua.country, ua.postal_code,\n" +
            "        oi.quantity,\n" +
            "        oi.unit_price,\n" +
            "        oi.discount,\n" +
            "        oi.total_price,\n" +
            "        p.name       AS product_name,\n" +
            "        p.sku,\n" +
            "        o.subtotal,\n" +
            "        o.tax_amount,\n" +
            "        o.shipping_charge,\n" +
            "        o.discount_amount,\n" +
            "        o.grand_total\n" +
            "    FROM orders o\n" +
            "    JOIN users u         ON u.id  = o.user_id\n" +
            "    JOIN order_items oi  ON oi.order_id   = o.id\n" +
            "    JOIN products p      ON p.id  = oi.product_id\n" +
            "    LEFT JOIN user_addresses ua ON ua.id = o.shipping_address_id\n" +
            "    WHERE o.id = p_order_id;\n" +
            "END";
        r = engine.execute(sqlGetOrderInvoice);
        assertTrue(r.success);

        String sqlPlaceOrder = "CREATE PROCEDURE sp_place_order(\n" +
            "    IN p_user_id          INT UNSIGNED,\n" +
            "    IN p_shipping_addr_id INT UNSIGNED,\n" +
            "    IN p_billing_addr_id  INT UNSIGNED,\n" +
            "    IN p_coupon_code      VARCHAR(50)\n" +
            ")\n" +
            "BEGIN\n" +
            "    DECLARE v_order_id      INT UNSIGNED;\n" +
            "    DECLARE v_subtotal      DECIMAL(12,2) DEFAULT 0;\n" +
            "    DECLARE v_discount      DECIMAL(10,2) DEFAULT 0;\n" +
            "    DECLARE v_tax           DECIMAL(10,2) DEFAULT 0;\n" +
            "    DECLARE v_shipping      DECIMAL(8,2)  DEFAULT 60.00;\n" +
            "    DECLARE v_grand_total   DECIMAL(12,2) DEFAULT 0;\n" +
            "    DECLARE v_order_number  VARCHAR(30);\n" +
            "    DECLARE v_coupon_id     INT UNSIGNED  DEFAULT NULL;\n" +
            "    DECLARE EXIT HANDLER FOR SQLEXCEPTION\n" +
            "    BEGIN\n" +
            "        ROLLBACK;\n" +
            "        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Order placement failed. Transaction rolled back.';\n" +
            "    END;\n" +
            "\n" +
            "    START TRANSACTION;\n" +
            "\n" +
            "    SELECT COALESCE(SUM(ci.quantity * ci.price), 0)\n" +
            "    INTO v_subtotal\n" +
            "    FROM cart_items ci\n" +
            "    JOIN carts c ON c.id = ci.cart_id\n" +
            "    WHERE c.user_id = p_user_id;\n" +
            "\n" +
            "    IF v_subtotal = 0 THEN\n" +
            "        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cart is empty.';\n" +
            "    END IF;\n" +
            "\n" +
            "    IF p_coupon_code IS NOT NULL THEN\n" +
            "        SELECT id, discount_type, discount_value, max_discount\n" +
            "        INTO v_coupon_id, @dtype, @dval, @maxd\n" +
            "        FROM coupons\n" +
            "        WHERE code = p_coupon_code\n" +
            "          AND status = 'active'\n" +
            "          AND (expires_at IS NULL OR expires_at > NOW())\n" +
            "          AND minimum_order_amount <= v_subtotal\n" +
            "        LIMIT 1;\n" +
            "\n" +
            "        IF v_coupon_id IS NOT NULL THEN\n" +
            "            SET v_discount = IF(@dtype = 'flat', @dval, LEAST(v_subtotal * @dval / 100, IFNULL(@maxd, 999999)));\n" +
            "        END IF;\n" +
            "    END IF;\n" +
            "\n" +
            "    SET v_tax        = ROUND((v_subtotal - v_discount) * 0.18, 2);\n" +
            "    SET v_grand_total = v_subtotal - v_discount + v_tax + v_shipping;\n" +
            "    SET v_order_number = CONCAT('ORD-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', LPAD(FLOOR(RAND()*99999), 5, '0'));\n" +
            "\n" +
            "    INSERT INTO orders (user_id, order_number, subtotal, tax_amount, shipping_charge,\n" +
            "                        discount_amount, grand_total, shipping_address_id, billing_address_id)\n" +
            "    VALUES (p_user_id, v_order_number, v_subtotal, v_tax, v_shipping,\n" +
            "            v_discount, v_grand_total, p_shipping_addr_id, p_billing_addr_id);\n" +
            "\n" +
            "    SET v_order_id = LAST_INSERT_ID();\n" +
            "\n" +
            "    INSERT INTO order_items (order_id, product_id, quantity, unit_price, discount, total_price)\n" +
            "    SELECT v_order_id, ci.product_id, ci.quantity, ci.price, 0, (ci.quantity * ci.price)\n" +
            "    FROM cart_items ci\n" +
            "    JOIN carts c ON c.id = ci.cart_id\n" +
            "    WHERE c.user_id = p_user_id;\n" +
            "\n" +
            "    IF v_coupon_id IS NOT NULL THEN\n" +
            "        INSERT INTO coupon_usage (coupon_id, user_id, order_id) VALUES (v_coupon_id, p_user_id, v_order_id);\n" +
            "    END IF;\n" +
            "\n" +
            "    DELETE ci FROM cart_items ci\n" +
            "    JOIN carts c ON c.id = ci.cart_id\n" +
            "    WHERE c.user_id = p_user_id;\n" +
            "\n" +
            "    COMMIT;\n" +
            "\n" +
            "    SELECT v_order_id AS order_id, v_order_number AS order_number, v_grand_total AS grand_total;\n" +
            "END";
        r = engine.execute(sqlPlaceOrder);
        assertTrue(r.success);

        // Verify they show up in SHOW PROCEDURE STATUS
        r = engine.execute("SHOW PROCEDURE STATUS WHERE Db = 'test_vp';");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());

        // Verify SHOW CREATE PROCEDURE works for them
        r = engine.execute("SHOW CREATE PROCEDURE sp_get_order_invoice;");
        assertTrue(r.success);
        assertTrue(((String) r.rows.get(0).get("Create Procedure")).contains("sp_get_order_invoice"));

        r = engine.execute("SHOW CREATE PROCEDURE sp_place_order;");
        assertTrue(r.success);
        assertTrue(((String) r.rows.get(0).get("Create Procedure")).contains("sp_place_order"));

        // Verify CALL procedure works
        r = engine.execute("CALL sp_get_order_invoice(1);");
        assertTrue(r.success);

        // Verify multiple columns ORDER BY sorting works
        engine.execute("INSERT INTO products (id, name, stock) VALUES (4, 'Banana2', 5);");
        r = engine.execute("SELECT * FROM products WHERE stock = 5 ORDER BY stock DESC, name ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("Apple", r.rows.get(0).get("name"));   // Apple < Banana2, ASC
        assertEquals("Banana2", r.rows.get(1).get("name"));
        engine.execute("DELETE FROM products WHERE id = 4;");

        // Verify JOIN ON clause with extra AND conditions
        engine.execute("CREATE TABLE reviews (id INT, product_id INT, rating INT, review_status VARCHAR(50));");
        engine.execute("INSERT INTO reviews VALUES (1, 1, 5, 'approved'), (2, 1, 4, 'pending');");
        r = engine.execute("SELECT p.name, r.rating AS rating FROM products p LEFT JOIN reviews r ON r.product_id = p.id AND r.review_status = 'approved' WHERE p.id = 1;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals(5L, r.rows.get(0).get("rating")); // pending review with rating 4 is filtered out from left join output
        engine.execute("DROP TABLE reviews;");

        // Clean up
        r = engine.execute("DROP PROCEDURE sp_get_order_invoice;");
        assertTrue(r.success);
        r = engine.execute("DROP PROCEDURE sp_place_order;");
        assertTrue(r.success);

        r = engine.execute("DROP TRIGGER trig_test;");
        assertTrue(r.success);

        r = engine.execute("DROP EVENT ev_test;");
        assertTrue(r.success);

        // Clean up mock tables
        engine.execute("DROP TABLE users;");
        engine.execute("DROP TABLE user_addresses;");
        engine.execute("DROP TABLE orders;");
        engine.execute("DROP TABLE order_items;");
        engine.execute("DROP TABLE cart_items;");
        engine.execute("DROP TABLE carts;");
        engine.execute("DROP TABLE coupons;");

        // Verify drops took effect
        r = engine.execute("SHOW FULL TABLES WHERE TABLE_TYPE = 'VIEW';");
        assertTrue(r.success);
        assertEquals(0, r.rows.size());
    }

    @Test
    public void testUnaryAndHavingOperatorCentralization() {
        engine.execute("CREATE DATABASE db_unary_test;");
        engine.execute("USE db_unary_test;");
        engine.execute("CREATE TABLE products (id INT, name TEXT, price DOUBLE);");
        engine.execute("INSERT INTO products VALUES (1, 'Apple', 1.50), (2, 'Banana', 0.75), (3, 'Orange', 2.00);");

        // 1. Unary minus in select projection
        QueryResult r = engine.execute("SELECT -price AS negative_price FROM products WHERE id = 1;");
        assertTrue(r.success);
        assertEquals(-1.50, (Double) r.rows.get(0).get("negative_price"), 0.0001);

        // 2. Unary plus in select projection
        r = engine.execute("SELECT +price AS positive_price FROM products WHERE id = 1;");
        assertTrue(r.success);
        assertEquals(1.50, (Double) r.rows.get(0).get("positive_price"), 0.0001);

        // 3. Unary NOT truthiness evaluations
        r = engine.execute("SELECT NOT 0 AS not_zero, NOT 1 AS not_one, NOT NOT 0 AS not_not_zero;");
        assertTrue(r.success);
        assertEquals(true, r.rows.get(0).get("not_zero"));
        assertEquals(false, r.rows.get(0).get("not_one"));
        assertEquals(false, r.rows.get(0).get("not_not_zero"));

        // 4. Unary NOT with comparison parenthesized
        r = engine.execute("SELECT NOT (price > 1.0) AS not_greater FROM products WHERE id = 2;");
        assertTrue(r.success);
        assertEquals(true, r.rows.get(0).get("not_greater")); // Banana's price is 0.75, which is not > 1.0, so NOT is true

        // 5. HAVING clause test using Group By
        r = engine.execute("SELECT name, SUM(price) AS total FROM products GROUP BY name HAVING SUM(price) > 1.0 ORDER BY name ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size()); // Apple (1.50), Orange (2.00)
        assertEquals("Apple", r.rows.get(0).get("name"));
        assertEquals("Orange", r.rows.get(1).get("name"));

        // 6. HAVING clause with lower bound (testing SqlOperator.compare)
        r = engine.execute("SELECT name, SUM(price) AS total FROM products GROUP BY name HAVING SUM(price) < 1.0;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("Banana", r.rows.get(0).get("name"));
    }

    @Test
    public void testShowColumns() {
        engine.execute("CREATE DATABASE db_show_columns;");
        engine.execute("USE db_show_columns;");
        engine.execute("CREATE TABLE users (id INT, name TEXT, balance DOUBLE);");

        // 1. Test SHOW COLUMNS FROM users
        QueryResult r = engine.execute("SHOW COLUMNS FROM users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("id", r.rows.get(0).get("Field"));
        assertEquals("INT", r.rows.get(0).get("Type"));
        assertEquals("name", r.rows.get(1).get("Field"));
        assertEquals("TEXT", r.rows.get(1).get("Type"));

        // 2. Test SHOW FULL COLUMNS FROM users
        r = engine.execute("SHOW FULL COLUMNS FROM users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());

        // 3. Test SHOW COLUMNS IN users
        r = engine.execute("SHOW COLUMNS IN users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
    }

    @Test
    public void testCompositeWhereFilters() {
        engine.execute("CREATE DATABASE db_composite_where;");
        engine.execute("USE db_composite_where;");
        engine.execute("CREATE TABLE students (id INT, name TEXT, course VARCHAR(50), semester INT);");
        engine.execute("INSERT INTO students VALUES " +
                       "(1, 'Amit', 'BCA', 1), " +
                       "(2, 'Rahul', 'BCA', 2), " +
                       "(3, 'Sneha', 'BSc', 1), " +
                       "(4, 'Priya', 'BTech', 1);");

        // 1. SELECT * FROM students WHERE course = 'BCA' AND semester = 1;
        QueryResult r = engine.execute("SELECT * FROM students WHERE course = 'BCA' AND semester = 1;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("Amit", r.rows.get(0).get("name"));

        // 2. SELECT * FROM students WHERE course = 'BCA' OR course = 'BSc';
        r = engine.execute("SELECT * FROM students WHERE course = 'BCA' OR course = 'BSc' ORDER BY id ASC;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size()); // Amit, Rahul, Sneha
        assertEquals("Amit", r.rows.get(0).get("name"));
        assertEquals("Rahul", r.rows.get(1).get("name"));
        assertEquals("Sneha", r.rows.get(2).get("name"));

        // 3. SELECT * FROM students WHERE NOT course = 'BCA';
        r = engine.execute("SELECT * FROM students WHERE NOT course = 'BCA' ORDER BY id ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size()); // Sneha, Priya
        assertEquals("Sneha", r.rows.get(0).get("name"));
        assertEquals("Priya", r.rows.get(1).get("name"));

        // 4. Parenthesized composite condition
        r = engine.execute("SELECT * FROM students WHERE (course = 'BCA' OR course = 'BSc') AND semester = 1 ORDER BY id ASC;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size()); // Amit, Sneha
        assertEquals("Amit", r.rows.get(0).get("name"));
        assertEquals("Sneha", r.rows.get(1).get("name"));
    }

    @Test
    public void testKeywordSuggester() {
        // 1. Suggest with empty/null input
        assertTrue(SqlKeywordSuggester.suggest("").isEmpty());
        assertTrue(SqlKeywordSuggester.suggest(null).isEmpty());

        // 2. Suggest keywords starting with 's' or 'S'
        List<String> sSuggestions = SqlKeywordSuggester.suggest("s");
        assertFalse(sSuggestions.isEmpty());
        assertTrue(sSuggestions.contains("SELECT"));
        assertTrue(sSuggestions.contains("SHOW"));
        assertTrue(sSuggestions.contains("START"));

        // 3. Suggest keywords starting with 'cre' (case-insensitive)
        List<String> creSuggestions = SqlKeywordSuggester.suggest("cre");
        assertEquals(1, creSuggestions.size());
        assertEquals("CREATE", creSuggestions.get(0));

        // 4. Verify all keywords can be retrieved
        List<String> allKeywords = SqlKeywordSuggester.getKeywords();
        assertFalse(allKeywords.isEmpty());
        assertTrue(allKeywords.contains("SELECT"));
    }

    @Test
    public void testSqlAliasExtractor() {
        // 1. Empty/null checks
        assertTrue(SqlAliasExtractor.extractAliases("").isEmpty());
        assertTrue(SqlAliasExtractor.extractAliases(null).isEmpty());

        // 2. Simple FROM with alias
        Map<String, String> a1 = SqlAliasExtractor.extractAliases("SELECT * FROM employees e");
        assertEquals(1, a1.size());
        assertEquals("employees", a1.get("e"));

        // 3. FROM with AS and alias
        Map<String, String> a2 = SqlAliasExtractor.extractAliases("SELECT * FROM employees AS emp WHERE emp.id = 1");
        assertEquals(1, a2.size());
        assertEquals("employees", a2.get("emp"));

        // 4. JOIN with aliases
        Map<String, String> a3 = SqlAliasExtractor.extractAliases(
            "SELECT e.name, d.name FROM employees e JOIN departments d ON e.dept_id = d.id"
        );
        assertEquals(2, a3.size());
        assertEquals("employees", a3.get("e"));
        assertEquals("departments", a3.get("d"));

        // 5. Comma-separated tables in FROM
        Map<String, String> a4 = SqlAliasExtractor.extractAliases(
            "SELECT * FROM table1 t1, table2 AS t2, table3 t3"
        );
        assertEquals(3, a4.size());
        assertEquals("table1", a4.get("t1"));
        assertEquals("table2", a4.get("t2"));
        assertEquals("table3", a4.get("t3"));

        // 6. DB.Table pattern
        Map<String, String> a5 = SqlAliasExtractor.extractAliases(
            "SELECT * FROM my_db.employees e"
        );
        assertEquals(1, a5.size());
        assertEquals("employees", a5.get("e"));

        // 7. Update alias
        Map<String, String> a6 = SqlAliasExtractor.extractAliases(
            "UPDATE employees e SET e.salary = 5000"
        );
        assertEquals(1, a6.size());
        assertEquals("employees", a6.get("e"));

        // 8. Incomplete query
        Map<String, String> a7 = SqlAliasExtractor.extractAliases(
            "SELECT * FROM employees e JOIN departments d ON e.dept_id = "
        );
        assertEquals(2, a7.size());
        assertEquals("employees", a7.get("e"));
        assertEquals("departments", a7.get("d"));
    }


    @Test
    public void testIndexSuggestionsAndSystemFunctions() throws Exception {
        // 1. Verify SqlKeywordSuggester contains SYSTEM_USER() and SESSION_USER()
        List<String> sysUserSuggestions = SqlKeywordSuggester.suggest("system");
        assertTrue(sysUserSuggestions.contains("SYSTEM_USER()"));
        List<String> sessUserSuggestions = SqlKeywordSuggester.suggest("session");
        assertTrue(sessUserSuggestions.contains("SESSION_USER()"));

        // 2. Setup database and table
        engine.execute("CREATE DATABASE db_suggest_test;");
        engine.execute("USE db_suggest_test;");
        engine.execute("CREATE TABLE t_suggest (id INT PRIMARY KEY, name VARCHAR(50) UNIQUE, age INT);");

        // Verify implicit indexes are registered
        List<String> indexes = engine.getIndexesList();
        assertTrue(indexes.contains("PRIMARY"));
        assertTrue(indexes.contains("uq_name"));

        // 3. Create index dynamically
        engine.execute("CREATE INDEX idx_age ON t_suggest (age);");
        indexes = engine.getIndexesList();
        assertTrue(indexes.contains("idx_age"));

        // Create unique index dynamically via ALTER TABLE
        engine.execute("ALTER TABLE t_suggest ADD UNIQUE idx_name_unique (name);");
        indexes = engine.getIndexesList();
        assertTrue(indexes.contains("idx_name_unique"));

        // 4. Drop index dynamically
        engine.execute("ALTER TABLE t_suggest DROP INDEX idx_age;");
        indexes = engine.getIndexesList();
        assertFalse(indexes.contains("idx_age"));

        // Cleanup
        engine.execute("DROP DATABASE db_suggest_test;");
    }

    @Test
    public void testSelectColumnValidation() throws Exception {
        engine.execute("CREATE DATABASE test_validation;");
        engine.execute("USE test_validation;");
        engine.execute("CREATE TABLE students (student_id INT PRIMARY KEY, student_name VARCHAR(100), email VARCHAR(100), city VARCHAR(50), phone VARCHAR(15));");
        engine.execute("INSERT INTO students VALUES (1, 'Amit', 'amit@gmail.com', 'Delhi', '123456');");

        // 1. SELECT MIN(semester) FROM students; should fail because 'semester' doesn't exist
        QueryResult r = engine.execute("SELECT MIN(semester) FROM students;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Unknown column 'semester' in 'field list'"));

        // 2. Unqualified column in projection should fail
        r = engine.execute("SELECT semester FROM students;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Unknown column 'semester' in 'field list'"));

        // 3. Qualified missing column in projection should fail
        r = engine.execute("SELECT students.semester FROM students;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Unknown column 'students.semester' in 'field list'"));

        // 4. Missing column in WHERE should fail
        r = engine.execute("SELECT student_name FROM students WHERE semester = 1;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Unknown column 'semester' in 'field list'"));

        // 5. Missing column in ORDER BY should fail
        r = engine.execute("SELECT student_name FROM students ORDER BY semester;");
        assertFalse(r.success);
        assertTrue(r.message.contains("Unknown column 'semester' in 'order clause'"));

        // 6. System constants/functions without table should pass
        r = engine.execute("SELECT SYSTEM_USER();");
        assertTrue(r.success);

        // 7. Wildcards should pass
        r = engine.execute("SELECT * FROM students;");
        assertTrue(r.success);

        // 8. Projection aliases in ORDER BY should pass
        r = engine.execute("SELECT student_name AS name FROM students ORDER BY name;");
        assertTrue(r.success);

        // Cleanup
        engine.execute("DROP DATABASE test_validation;");
    }

    @Test
    public void testCaseInsensitivity() throws Exception {
        // 1. Database name case-insensitivity
        QueryResult r = engine.execute("CREATE DATABASE DB_CASE_TEST;");
        assertTrue(r.success);

        r = engine.execute("USE db_case_test;");
        assertTrue(r.success);

        // 2. Table name case-insensitivity during creation/query
        r = engine.execute("CREATE TABLE USERS_TEST (ID INT PRIMARY KEY, NAME VARCHAR(50));");
        assertTrue(r.success);

        r = engine.execute("INSERT INTO users_test VALUES (1, 'John'), (2, 'Jane');");
        assertTrue(r.success);

        // Check that querying with uppercase table name works
        r = engine.execute("SELECT * FROM USERS_TEST;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());

        // Check that querying with lowercase table name works
        r = engine.execute("SELECT * FROM users_test;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());

        // 3. Column name case-insensitivity in WHERE & Projection
        r = engine.execute("SELECT ID, name FROM USERS_TEST WHERE Name = 'John';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals(1L, r.rows.get(0).get("ID"));

        r = engine.execute("SELECT id, NAME FROM users_test WHERE name = 'John';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals(1L, r.rows.get(0).get("id"));

        // 4. Case-insensitivity in JOIN ON clause & outputs
        r = engine.execute("CREATE TABLE ORDERS_TEST (ORDER_ID INT, USER_ID INT, AMOUNT DOUBLE);");
        assertTrue(r.success);

        r = engine.execute("INSERT INTO orders_test VALUES (100, 1, 50.5), (200, 2, 75.0);");
        assertTrue(r.success);

        // Run join with mixed case ON condition columns
        r = engine.execute("SELECT users_test.NAME, orders_test.AMOUNT FROM USERS_TEST JOIN ORDERS_TEST ON users_test.ID = orders_test.USER_ID;");
        assertTrue(r.success);
        assertEquals(2, r.rows.size());
        assertEquals("John", r.rows.get(0).get("users_test.NAME"));
        assertEquals(50.5, r.rows.get(0).get("orders_test.AMOUNT"));
        assertEquals("Jane", r.rows.get(1).get("users_test.NAME"));
        assertEquals(75.0, r.rows.get(1).get("orders_test.AMOUNT"));

        // Cleanup
        engine.execute("DROP DATABASE DB_CASE_TEST;");
    }

    @Test
    public void testSqlHelpCommand() {
        // Test HELP general
        QueryResult r = engine.execute("HELP;");
        assertTrue(r.success);
        assertNotNull(r.columns);
        assertEquals(2, r.columns.size());
        assertEquals("Keyword", r.columns.get(0));
        assertEquals("Description", r.columns.get(1));
        assertTrue(r.rows.size() > 10);

        // Test HELP specific topic (direct match)
        r = engine.execute("HELP SELECT;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("SELECT", r.rows.get(0).get("Keyword"));
        assertTrue(r.rows.get(0).get("Description").toString().contains("Retrieves data"));

        // Test HELP specific topic (lowercase and quote)
        r = engine.execute("HELP 'insert';");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("INSERT", r.rows.get(0).get("Keyword"));

        // Test HELP substring match
        r = engine.execute("HELP CREATE;");
        assertTrue(r.success);
        assertTrue(r.rows.size() > 1);

        // Test HELP no match
        r = engine.execute("HELP UNKNOWN_TOPIC;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertTrue(r.rows.get(0).get("Description").toString().contains("No help available"));
    }

    @Test
    public void testInformationSchemaViewsAndUnknownFunctions() throws Exception {
        engine.execute("CREATE DATABASE test_vp_meta;");
        engine.execute("USE test_vp_meta;");
        engine.execute("CREATE TABLE products (id INT, name VARCHAR(100));");
        engine.execute("CREATE VIEW v_prod AS SELECT * FROM products;");

        // Query INFORMATION_SCHEMA.VIEWS
        QueryResult r = engine.execute("SELECT TABLE_NAME, TABLE_SCHEMA, VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = DATABASE();");
        assertTrue(r.message, r.success);
        assertEquals(1, r.rows.size());
        assertEquals("v_prod", r.rows.get(0).get("TABLE_NAME"));
        assertEquals("test_vp_meta", r.rows.get(0).get("TABLE_SCHEMA"));

        // Query unknown function
        QueryResult rFunc = engine.execute("SELECT fn_order_total_fake(10);");
        assertFalse(rFunc.success);
        assertTrue(rFunc.message, rFunc.message.toLowerCase().contains("fn_order_total_fake"));

        // Cleanup
        engine.execute("DROP VIEW v_prod;");
        engine.execute("DROP TABLE products;");
        engine.execute("DROP DATABASE test_vp_meta;");
    }

    @Test
    public void testRoutineMetadataAndDefaultParsing() throws Exception {
        engine.execute("CREATE DATABASE test_routines;");
        engine.execute("USE test_routines;");

        // Create a custom function using DECLARE with DEFAULT
        QueryResult rCreate = engine.execute(
            "CREATE FUNCTION fn_multiply_add(val INT) RETURNS INT\n" +
            "BEGIN\n" +
            "    DECLARE factor INT DEFAULT 5;\n" +
            "    DECLARE offset_val INT DEFAULT 10;\n" +
            "    RETURN val * factor + offset_val;\n" +
            "END;"
        );
        assertTrue(rCreate.message, rCreate.success);

        // Call the custom function
        QueryResult rCall = engine.execute("SELECT fn_multiply_add(3);");
        assertTrue(rCall.message, rCall.success);
        assertEquals(1, rCall.rows.size());
        // 3 * 5 + 10 = 25
        Object val = rCall.rows.get(0).values().iterator().next();
        assertEquals(25L, ((Number) val).longValue());

        // Test SHOW FUNCTION STATUS with WHERE clause
        QueryResult rShow = engine.execute("SHOW FUNCTION STATUS WHERE Db = DATABASE();");
        assertTrue(rShow.message, rShow.success);
        assertTrue(rShow.rows.size() >= 1);
        assertEquals("fn_multiply_add", rShow.rows.get(0).get("Name"));
        assertEquals("FUNCTION", rShow.rows.get(0).get("Type"));
        assertEquals("test_routines", rShow.rows.get(0).get("Db"));

        // Test INFORMATION_SCHEMA.ROUTINES
        QueryResult rRoutines = engine.execute(
            "SELECT ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_SCHEMA, DATA_TYPE " +
            "FROM INFORMATION_SCHEMA.ROUTINES " +
            "WHERE ROUTINE_TYPE = 'FUNCTION' AND ROUTINE_SCHEMA = DATABASE();"
        );
        assertTrue(rRoutines.message, rRoutines.success);
        assertEquals(1, rRoutines.rows.size());
        assertEquals("fn_multiply_add", rRoutines.rows.get(0).get("ROUTINE_NAME"));
        assertEquals("FUNCTION", rRoutines.rows.get(0).get("ROUTINE_TYPE"));
        assertEquals("test_routines", rRoutines.rows.get(0).get("ROUTINE_SCHEMA"));
        assertEquals("INT", rRoutines.rows.get(0).get("DATA_TYPE"));

        // Cleanup
        engine.execute("DROP FUNCTION fn_multiply_add;");
        engine.execute("DROP DATABASE test_routines;");
    }

    @Test
    public void testShowCreateAndSelectIntoAndCallUserVar() throws Exception {
        engine.execute("CREATE DATABASE test_routines_2;");
        engine.execute("USE test_routines_2;");

        // Create table and view
        engine.execute("CREATE TABLE orders (id INT, amount DECIMAL(10,2));");
        engine.execute("CREATE VIEW vw_orders AS SELECT * FROM orders;");

        // Verify SHOW CREATE VIEW
        QueryResult rView = engine.execute("SHOW CREATE VIEW vw_orders;");
        assertTrue(rView.message, rView.success);
        assertEquals(1, rView.rows.size());
        assertTrue(rView.rows.get(0).get("Create View").toString().contains("CREATE VIEW"));

        // Create a function using SELECT INTO
        QueryResult rCreate = engine.execute(
            "CREATE FUNCTION fn_order_sum(p_id INT) RETURNS DECIMAL(10,2)\n" +
            "BEGIN\n" +
            "    DECLARE v_sum DECIMAL(10,2) DEFAULT 0.0;\n" +
            "    SELECT amount INTO v_sum FROM orders WHERE id = p_id;\n" +
            "    RETURN v_sum;\n" +
            "END;"
        );
        assertTrue(rCreate.message, rCreate.success);

        // Verify SHOW CREATE FUNCTION
        QueryResult rFunc = engine.execute("SHOW CREATE FUNCTION fn_order_sum;");
        assertTrue(rFunc.message, rFunc.success);
        assertEquals(1, rFunc.rows.size());
        assertTrue(rFunc.rows.get(0).get("Create Function").toString().contains("CREATE FUNCTION"));

        // Insert mock data and execute SELECT INTO inside UDF
        engine.execute("INSERT INTO orders (id, amount) VALUES (1, 150.00);");
        QueryResult rCall = engine.execute("SELECT fn_order_sum(1);");
        assertTrue(rCall.message, rCall.success);
        assertEquals(1, rCall.rows.size());
        Object val = rCall.rows.get(0).values().iterator().next();
        assertEquals(150.00, ((Number) val).doubleValue(), 0.001);

        // Verify CALL with user variable prefixed with @
        engine.execute("CREATE PROCEDURE sp_create_order(p1 INT, p2 VARCHAR(50), p3 DECIMAL(10,2), p4 VARCHAR(20), p5 INT) BEGIN END;");
        QueryResult rCallProc = engine.execute("CALL sp_create_order(1, 'MP Bhopal', 2500.00, 'Pending', @new_order_id);");
        assertTrue(rCallProc.message, rCallProc.success);
        assertEquals(1L, ((Number) engine.getUserVariable("@new_order_id")).longValue());

        // Cleanup
        engine.execute("DROP VIEW vw_orders;");
        engine.execute("DROP TABLE orders;");
        engine.execute("DROP FUNCTION fn_order_sum;");
        engine.execute("DROP PROCEDURE sp_create_order;");
        engine.execute("DROP DATABASE test_routines_2;");
    }

    @Test
    public void testViewFunctionProcedureFormatting() throws Exception {
        engine.execute("CREATE DATABASE test_format;");
        engine.execute("USE test_format;");

        // 1. View formatting test
        engine.execute("CREATE TABLE products (id INT, price DECIMAL(10,2));");
        engine.execute("CREATE VIEW vw_formatted AS SELECT id, price FROM products WHERE price > 50.00;");

        QueryResult rView = engine.execute("SHOW CREATE VIEW vw_formatted;");
        assertTrue(rView.success);
        String viewDdl = rView.rows.get(0).get("Create View").toString();
        String expectedView = "CREATE VIEW vw_formatted AS\nSELECT id, price\nFROM products\nWHERE price > 50.00";
        assertEquals(expectedView, viewDdl);

        // 2. Function formatting test
        engine.execute(
            "CREATE FUNCTION fn_format_test(p_id INT) RETURNS DECIMAL(10,2)\n" +
            "BEGIN\n" +
            "    DECLARE v_val DECIMAL(10,2) DEFAULT 0.0;\n" +
            "    SET v_val = 100.00;\n" +
            "    RETURN v_val;\n" +
            "END;"
        );
        QueryResult rFunc = engine.execute("SHOW CREATE FUNCTION fn_format_test;");
        assertTrue(rFunc.success);
        String funcDdl = rFunc.rows.get(0).get("Create Function").toString();
        String expectedFunc = "CREATE FUNCTION fn_format_test(p_id INT) RETURNS DECIMAL\nBEGIN\n    DECLARE v_val DECIMAL(10, 2) DEFAULT 0.0;\n    SET v_val = 100.00;\n    RETURN v_val;\nEND";
        assertEquals(expectedFunc, funcDdl);

        // 3. Procedure formatting test
        engine.execute(
            "CREATE PROCEDURE sp_format_test()\n" +
            "BEGIN\n" +
            "    DECLARE x INT DEFAULT 0;\n" +
            "    WHILE x < 10 DO\n" +
            "        SET x = x + 1;\n" +
            "    END WHILE;\n" +
            "END;"
        );
        QueryResult rProc = engine.execute("SHOW CREATE PROCEDURE sp_format_test;");
        assertTrue(rProc.success);
        String procDdl = rProc.rows.get(0).get("Create Procedure").toString();
        String expectedProc = "CREATE PROCEDURE sp_format_test()\nBEGIN\n    DECLARE x INT DEFAULT 0;\n    WHILE x < 10 DO\n        SET x = x + 1;\n    END WHILE;\nEND";
        assertEquals(expectedProc, procDdl);

        // Cleanup
        engine.execute("DROP VIEW vw_formatted;");
        engine.execute("DROP TABLE products;");
        engine.execute("DROP FUNCTION fn_format_test;");
        engine.execute("DROP PROCEDURE sp_format_test;");
        engine.execute("DROP DATABASE test_format;");
    }

    @Test
    public void testUserDefinedVariables() throws Exception {
        engine.execute("CREATE DATABASE test_vars;");
        engine.execute("USE test_vars;");

        // 1. Test SET and SELECT user variable
        QueryResult rSet = engine.execute("SET @order_no = 5;");
        assertTrue(rSet.message, rSet.success);

        QueryResult rSelectVar = engine.execute("SELECT @order_no;");
        assertTrue(rSelectVar.message, rSelectVar.success);
        assertEquals(1, rSelectVar.rows.size());
        assertEquals(5L, ((Number) rSelectVar.rows.get(0).get("@order_no")).longValue());

        // 2. Test using user variable in WHERE clause
        engine.execute("CREATE TABLE orders (order_id INT, amount DECIMAL(10,2));");
        engine.execute("INSERT INTO orders (order_id, amount) VALUES (3, 10.00), (5, 25.50), (7, 45.00);");

        QueryResult rFilter = engine.execute("SELECT * FROM orders WHERE order_id = @order_no;");
        assertTrue(rFilter.message, rFilter.success);
        assertEquals(1, rFilter.rows.size());
        assertEquals(25.50, ((Number) rFilter.rows.get(0).get("amount")).doubleValue(), 0.001);

        // 3. Test changing user variable and filtering again
        engine.execute("SET @order_no = 7;");
        QueryResult rFilter2 = engine.execute("SELECT * FROM orders WHERE order_id = @order_no;");
        assertTrue(rFilter2.message, rFilter2.success);
        assertEquals(1, rFilter2.rows.size());
        assertEquals(45.00, ((Number) rFilter2.rows.get(0).get("amount")).doubleValue(), 0.001);

        // Cleanup
        engine.execute("DROP TABLE orders;");
        engine.execute("DROP DATABASE test_vars;");

        // 4. Test selecting a non-existent variable
        QueryResult rNonExistent = engine.execute("SELECT @non_existent;");
        assertTrue(rNonExistent.success);
        assertEquals(1, rNonExistent.rows.size());
        assertNull(rNonExistent.rows.get(0).get("@non_existent"));
    }

    @Test
    public void testIndexFeaturesAndSystemStatistics() throws Exception {
        engine.execute("CREATE DATABASE test_idx;");
        engine.execute("USE test_idx;");

        // 1. Create table with unique constraint and optional index name
        QueryResult rCreate = engine.execute(
            "CREATE TABLE products (" +
            "  id INT PRIMARY KEY AUTO_INCREMENT," +
            "  name VARCHAR(255) NOT NULL," +
            "  sku VARCHAR(50) NOT NULL," +
            "  created_at DATETIME NOT NULL DEFAULT NOW()," +
            "  UNIQUE KEY uq_products_sku (sku)," +
            "  UNIQUE KEY (name)" + // without index name
            ");"
        );
        assertTrue(rCreate.message, rCreate.success);

        // Test default value insertion
        QueryResult rInsert = engine.execute("INSERT INTO products (name, sku) VALUES ('Product A', 'SKU-A');");
        assertTrue(rInsert.message, rInsert.success);

        QueryResult rSelect = engine.execute("SELECT * FROM products;");
        assertTrue(rSelect.message, rSelect.success);
        assertEquals(1, rSelect.rows.size());
        assertNotNull(rSelect.rows.get(0).get("created_at"));

        // 2. Alter table add fulltext/spatial index
        QueryResult rAlter1 = engine.execute("ALTER TABLE products ADD FULLTEXT INDEX ft_products_name (name);");
        assertTrue(rAlter1.message, rAlter1.success);

        QueryResult rAlter2 = engine.execute("ALTER TABLE products ADD SPATIAL KEY (sku);"); // without name
        assertTrue(rAlter2.message, rAlter2.success);

        // 3. Verify SHOW INDEX / INDEXES / KEYS
        QueryResult rShow1 = engine.execute("SHOW INDEX FROM products;");
        assertTrue(rShow1.message, rShow1.success);
        assertTrue(rShow1.rows.size() >= 4);

        QueryResult rShow2 = engine.execute("SHOW INDEXES FROM products;");
        assertTrue(rShow2.message, rShow2.success);
        assertEquals(rShow1.rows.size(), rShow2.rows.size());

        QueryResult rShow3 = engine.execute("SHOW KEYS FROM products;");
        assertTrue(rShow3.message, rShow3.success);
        assertEquals(rShow1.rows.size(), rShow3.rows.size());

        // Validate index details in SHOW INDEX
        Map<String, Object> pkRow = null;
        Map<String, Object> uqRow = null;
        Map<String, Object> ftRow = null;
        Map<String, Object> spatialRow = null;

        for (Map<String, Object> row : rShow1.rows) {
            String keyName = (String) row.get("Key_name");
            if ("PRIMARY".equalsIgnoreCase(keyName)) {
                pkRow = row;
            } else if ("uq_products_sku".equalsIgnoreCase(keyName)) {
                uqRow = row;
            } else if ("ft_products_name".equalsIgnoreCase(keyName)) {
                ftRow = row;
            } else if (keyName != null && keyName.startsWith("idx_")) {
                spatialRow = row;
            }
        }

        assertNotNull("PRIMARY index not found", pkRow);
        assertEquals(0L, ((Number) pkRow.get("Non_unique")).longValue());
        assertEquals("BTREE", pkRow.get("Index_type"));

        assertNotNull("uq_products_sku index not found", uqRow);
        assertEquals(0L, ((Number) uqRow.get("Non_unique")).longValue());
        assertEquals("BTREE", uqRow.get("Index_type"));

        assertNotNull("ft_products_name index not found", ftRow);
        assertEquals(1L, ((Number) ftRow.get("Non_unique")).longValue());
        assertEquals("FULLTEXT", ftRow.get("Index_type"));

        assertNotNull("spatial index not found", spatialRow);
        assertEquals(1L, ((Number) spatialRow.get("Non_unique")).longValue());
        assertEquals("SPATIAL", spatialRow.get("Index_type"));

        // 4. Verify INFORMATION_SCHEMA.STATISTICS
        QueryResult rStats = engine.execute("SELECT * FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_NAME = 'products';");
        assertTrue(rStats.message, rStats.success);
        assertTrue(rStats.rows.size() >= 4);

        // Cleanup
        engine.execute("DROP TABLE products;");
        engine.execute("DROP DATABASE test_idx;");
    }

    @Test
    public void testPartitionsAndOnDuplicateKeyUpdate() throws Exception {
        engine.execute("CREATE DATABASE test_part;");
        engine.execute("USE test_part;");

        // 1. Create table with partitioning syntax (ignores it during parsing)
        QueryResult rCreate = engine.execute(
            "CREATE TABLE IF NOT EXISTS orders_partitioned (" +
            "    order_id INT NOT NULL," +
            "    user_id INT NOT NULL," +
            "    order_date DATETIME NOT NULL," +
            "    status VARCHAR(30) NOT NULL," +
            "    total_amount DECIMAL(10,2) NOT NULL," +
            "    shipping_address VARCHAR(250)," +
            "    PRIMARY KEY (order_id, order_date)" +
            ") PARTITION BY RANGE (YEAR(order_date)) (" +
            "    PARTITION p2025 VALUES LESS THAN (2026)," +
            "    PARTITION p2026 VALUES LESS THAN (2027)," +
            "    PARTITION p2027 VALUES LESS THAN (2028)," +
            "    PARTITION p_future VALUES LESS THAN MAXVALUE" +
            ");"
        );
        assertTrue(rCreate.message, rCreate.success);

        // 2. Create product_attributes table with unique key
        QueryResult rCreateAttrs = engine.execute(
            "CREATE TABLE product_attributes (" +
            "    product_id INT PRIMARY KEY," +
            "    attributes TEXT" +
            ");"
        );
        assertTrue(rCreateAttrs.message, rCreateAttrs.success);

        // 3. Test insert values
        QueryResult rInsert1 = engine.execute(
            "INSERT INTO product_attributes (product_id, attributes) VALUES " +
            "(1, '{\"color\": \"black\"}'), " +
            "(5, '{\"color\": \"white\"}');"
        );
        assertTrue(rInsert1.message, rInsert1.success);
        assertEquals(2, rInsert1.affectedRows);

        // 4. Test insert ON DUPLICATE KEY UPDATE
        QueryResult rInsert2 = engine.execute(
            "INSERT INTO product_attributes (product_id, attributes) VALUES " +
            "(1, '{\"color\": \"black\", \"weight\": \"20g\"}'), " +
            "(10, '{\"color\": \"silver\"}') " +
            "ON DUPLICATE KEY UPDATE attributes = VALUES(attributes);"
        );
        assertTrue(rInsert2.message, rInsert2.success);
        assertEquals(3, rInsert2.affectedRows);

        // 5. Verify the updated row values
        QueryResult rSelect = engine.execute("SELECT * FROM product_attributes ORDER BY product_id;");
        assertTrue(rSelect.message, rSelect.success);
        assertEquals(3, rSelect.rows.size());
        
        // Row 1 (product_id 1) should have updated weight attributes
        assertEquals("{\"color\": \"black\", \"weight\": \"20g\"}", rSelect.rows.get(0).get("attributes"));
        // Row 3 (product_id 10) should be silver
        assertEquals("{\"color\": \"silver\"}", rSelect.rows.get(2).get("attributes"));

        // Cleanup
        engine.execute("DROP TABLE orders_partitioned;");
        engine.execute("DROP TABLE product_attributes;");
        engine.execute("DROP DATABASE test_part;");
    }

    @Test
    public void testViewProcedureFunctionValidation() throws Exception {
        engine.execute("CREATE DATABASE test_val_db;");
        engine.execute("USE test_val_db;");

        // Create a basic table
        engine.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50), email VARCHAR(50));");

        // 1. Test view validation: invalid table name
        QueryResult rViewFail1 = engine.execute("CREATE VIEW v_users_fail AS SELECT * FROM non_existent_table;");
        assertFalse(rViewFail1.success);
        assertTrue(rViewFail1.message.contains("does not exist"));

        // 2. Test view validation: invalid column name
        QueryResult rViewFail2 = engine.execute("CREATE VIEW v_users_fail2 AS SELECT non_existent_col FROM users;");
        assertFalse(rViewFail2.success);
        assertTrue(rViewFail2.message.contains("Unknown column"));

        // 3. Test view validation: UNION with invalid column in second select
        QueryResult rViewUnionFail = engine.execute(
            "CREATE VIEW v_union_fail AS " +
            "SELECT name FROM users " +
            "UNION " +
            "SELECT bad_col FROM users;"
        );
        assertFalse(rViewUnionFail.success);
        assertTrue(rViewUnionFail.message.contains("Unknown column"));

        // 4. Test view validation: correct view
        QueryResult rViewSuccess = engine.execute("CREATE VIEW v_users_success AS SELECT id, name FROM users;");
        assertTrue(rViewSuccess.success);

        // 5. Test function validation: invalid column inside SELECT INTO
        String fnFail = 
            "CREATE FUNCTION get_user_email(p_id INT) RETURNS VARCHAR(50) " +
            "BEGIN " +
            "    DECLARE v_email VARCHAR(50); " +
            "    SELECT bad_column INTO v_email FROM users WHERE id = p_id; " +
            "    RETURN v_email; " +
            "END";
        QueryResult rFnFail = engine.execute(fnFail);
        assertFalse(rFnFail.success);
        assertTrue(rFnFail.message.contains("Unknown column"));

        // 6. Test function validation: successful creation (ignores parameters and local variables as invalid columns)
        String fnSuccess = 
            "CREATE FUNCTION get_user_email_ok(p_id INT) RETURNS VARCHAR(50) " +
            "BEGIN " +
            "    DECLARE v_email VARCHAR(50); " +
            "    SELECT email INTO v_email FROM users WHERE id = p_id; " +
            "    RETURN v_email; " +
            "END";
        QueryResult rFnSuccess = engine.execute(fnSuccess);
        assertTrue(rFnSuccess.message, rFnSuccess.success);

        // 7. Test procedure validation: invalid column inside standard SELECT
        String procFail = 
            "CREATE PROCEDURE get_users_fail() " +
            "BEGIN " +
            "    SELECT name, invalid_col FROM users; " +
            "END";
        QueryResult rProcFail = engine.execute(procFail);
        assertFalse(rProcFail.success);
        assertTrue(rProcFail.message.contains("Unknown column"));

        // 8. Test procedure validation: successful creation (ignores parameters and local variables)
        String procSuccess = 
            "CREATE PROCEDURE get_users_ok(IN p_id INT) " +
            "BEGIN " +
            "    DECLARE v_name VARCHAR(50); " +
            "    SELECT name INTO v_name FROM users WHERE id = p_id; " +
            "    SELECT v_name; " +
            "END";
        QueryResult rProcSuccess = engine.execute(procSuccess);
        assertTrue(rProcSuccess.message, rProcSuccess.success);

        // Cleanup
        engine.execute("DROP VIEW v_users_success;");
        engine.execute("DROP FUNCTION get_user_email_ok;");
        engine.execute("DROP PROCEDURE get_users_ok;");
        engine.execute("DROP TABLE users;");
        engine.execute("DROP DATABASE test_val_db;");
    }

    @Test
    public void testDatabaseImportExportAllFormats() throws Exception {
        // Prepare database and test data
        engine.execute("CREATE DATABASE export_test_db;");
        engine.execute("USE export_test_db;");
        engine.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), score DOUBLE, CHECK (score >= 0));");
        engine.execute("INSERT INTO users (name, score) VALUES ('Alice', 95.5), ('Bob', 82.0), ('Charlie', NULL);");
        engine.execute("CREATE VIEW v_users AS SELECT name, score FROM users;");
        
        File exportDir = new File("build/test-export");
        if (exportDir.exists()) {
            deleteRecursive(exportDir);
        }
        exportDir.mkdirs();

        // 1. Test .db (ZIP native)
        String dbFile = "build/test-export/backup.db";
        QueryResult r = engine.execute("EXPORT DATABASE export_test_db TO '" + dbFile + "';");
        assertTrue(r.message, r.success);
        assertTrue(new File(dbFile).exists() && new File(dbFile).length() > 0);

        engine.execute("DROP DATABASE export_test_db;");
        r = engine.execute("IMPORT DATABASE export_test_db FROM '" + dbFile + "';");
        assertTrue(r.message, r.success);
        engine.execute("USE export_test_db;");
        r = engine.execute("SELECT * FROM users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("Alice", r.rows.get(0).get("name"));
        assertEquals(95.5, ((Number) r.rows.get(0).get("score")).doubleValue(), 0.001);

        // Verify view exists
        r = engine.execute("SELECT * FROM v_users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());

        // 2. Test .sql format
        String sqlFile = "build/test-export/backup.sql";
        r = engine.execute("EXPORT DATABASE export_test_db TO '" + sqlFile + "';");
        assertTrue(r.message, r.success);
        assertTrue(new File(sqlFile).exists() && new File(sqlFile).length() > 0);

        engine.execute("DROP DATABASE export_test_db;");
        r = engine.execute("IMPORT DATABASE export_test_db FROM '" + sqlFile + "';");
        assertTrue(r.message, r.success);
        engine.execute("USE export_test_db;");
        r = engine.execute("SELECT * FROM users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        assertEquals("Bob", r.rows.get(1).get("name"));

        // 3. Test .csv format
        String csvFile = "build/test-export/backup.csv";
        r = engine.execute("EXPORT DATABASE export_test_db TO '" + csvFile + "';");
        assertTrue(r.message, r.success);
        assertTrue(new File(csvFile).exists() && new File(csvFile).length() > 0);

        engine.execute("DROP DATABASE export_test_db;");
        r = engine.execute("IMPORT DATABASE export_test_db FROM '" + csvFile + "';");
        assertTrue(r.message, r.success);
        engine.execute("USE export_test_db;");
        // Check if table is imported
        r = engine.execute("SELECT * FROM users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        // Verify type inference: id should be INT, score should be DOUBLE, name should be TEXT
        r = engine.execute("DESCRIBE users;");
        assertTrue(r.success);
        assertEquals("INT", r.rows.get(0).get("Type"));
        assertEquals("TEXT", r.rows.get(1).get("Type"));
        assertEquals("DOUBLE", r.rows.get(2).get("Type"));

        // 4. Test .xlsx (SpreadsheetML) format
        String xlsxFile = "build/test-export/backup.xlsx";
        r = engine.execute("EXPORT DATABASE export_test_db TO '" + xlsxFile + "';");
        assertTrue(r.message, r.success);
        assertTrue(new File(xlsxFile).exists() && new File(xlsxFile).length() > 0);

        engine.execute("DROP DATABASE export_test_db;");
        r = engine.execute("IMPORT DATABASE export_test_db FROM '" + xlsxFile + "';");
        assertTrue(r.message, r.success);
        engine.execute("USE export_test_db;");
        r = engine.execute("SELECT * FROM users;");
        assertTrue(r.success);
        assertEquals(3, r.rows.size());
        
        // Clean up
        engine.execute("DROP DATABASE export_test_db;");
        deleteRecursive(exportDir);
    }

    @Test
    public void testExportWithDeferWrite() throws Exception {
        engine.execute("CREATE DATABASE defer_db;");
        engine.execute("USE defer_db;");
        engine.execute("CREATE TABLE users (id INT PRIMARY KEY, name TEXT);");
        
        // Enable deferWrite
        engine.setDeferWrite(true);
        
        // Insert data (remains dirty in memory cache)
        engine.execute("INSERT INTO users VALUES (1, 'John Doe');");
        
        File exportDir = new File("build/test-export-defer");
        if (exportDir.exists()) {
            deleteRecursive(exportDir);
        }
        exportDir.mkdirs();
        
        String csvFile = "build/test-export-defer/backup.csv";
        // Export should force a flush of the dirty table
        QueryResult r = engine.execute("EXPORT DATABASE defer_db TO '" + csvFile + "';");
        assertTrue(r.message, r.success);
        assertTrue(new File(csvFile).exists() && new File(csvFile).length() > 0);
        
        // Re-import and check
        engine.execute("DROP DATABASE defer_db;");
        r = engine.execute("IMPORT DATABASE defer_db FROM '" + csvFile + "';");
        assertTrue(r.message, r.success);
        
        engine.execute("USE defer_db;");
        r = engine.execute("SELECT * FROM users;");
        assertTrue(r.success);
        assertEquals(1, r.rows.size());
        assertEquals("John Doe", r.rows.get(0).get("name"));
        
        // Clean up
        engine.execute("DROP DATABASE defer_db;");
        deleteRecursive(exportDir);
    }

    @Test
    public void testSqlExportWithDefinitionsAndTriggersEvents() throws Exception {
        engine.execute("CREATE DATABASE def_test_db;");
        engine.execute("USE def_test_db;");
        
        // Create table with specific formatting
        String createTableSql = "CREATE TABLE users (\n" +
                "  id INT NOT NULL,\n" +
                "  name VARCHAR(50)\n" +
                ");";
        engine.execute(createTableSql);
        
        // Create view
        String createViewSql = "CREATE VIEW v_users AS SELECT id, name FROM users;";
        engine.execute(createViewSql);
        
        // Create trigger
        String createTriggerSql = "CREATE TRIGGER trg_after_insert AFTER INSERT ON users FOR EACH ROW BEGIN END";
        engine.execute(createTriggerSql);
        
        // Create event
        String createEventSql = "CREATE EVENT ev_cleanup ON SCHEDULE EVERY 1 HOUR DO BEGIN END";
        engine.execute(createEventSql);

        // Create UDF function
        String createFunctionSql = "CREATE FUNCTION fn_get_name(p_id INT) RETURNS VARCHAR\n" +
                "BEGIN\n" +
                "    RETURN 'test';\n" +
                "END";
        engine.execute(createFunctionSql);

        File exportDir = new File("build/test-export-def");
        if (exportDir.exists()) {
            deleteRecursive(exportDir);
        }
        exportDir.mkdirs();

        String sqlFile = "build/test-export-def/backup.sql";
        QueryResult r = engine.execute("EXPORT DATABASE def_test_db TO '" + sqlFile + "';");
        assertTrue(r.message, r.success);
        assertTrue(new File(sqlFile).exists());

        // Read file content
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(sqlFile));
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("CREATE TABLE users"));
        assertTrue(content.contains("CREATE VIEW v_users AS SELECT"));
        assertTrue(content.contains("CREATE TRIGGER trg_after_insert"));
        assertTrue(content.contains("CREATE EVENT ev_cleanup"));
        assertTrue(content.contains("CREATE FUNCTION fn_get_name"));

        // Drop and Re-import
        engine.execute("DROP DATABASE def_test_db;");
        r = engine.execute("IMPORT DATABASE def_test_db FROM '" + sqlFile + "';");
        assertTrue(r.message, r.success);

        // Verify imported successfully
        engine.execute("USE def_test_db;");
        r = engine.execute("SELECT * FROM users;");
        assertTrue(r.message, r.success);

        // Clean up
        engine.execute("DROP DATABASE def_test_db;");
        deleteRecursive(exportDir);
    }

    @Test
    public void testUpdateWithReplaceFunction() {
        engine.execute("CREATE DATABASE update_test_db;");
        engine.execute("USE update_test_db;");
        engine.execute("CREATE TABLE articles (id INT PRIMARY KEY, content VARCHAR(255), views INT);");
        engine.execute("INSERT INTO articles VALUES (1, 'hello old_text world', 10), (2, 'sample text without match', 20);");

        // Test UPDATE with REPLACE function
        QueryResult r1 = engine.execute("UPDATE articles SET content = REPLACE(content, 'old_text', 'new_text') WHERE id = 1;");
        assertTrue(r1.message, r1.success);
        assertEquals(1, r1.affectedRows);

        // Verify content updated for id 1
        QueryResult r2 = engine.execute("SELECT content FROM articles WHERE id = 1;");
        assertTrue(r2.message, r2.success);
        assertEquals(1, r2.rows.size());
        assertEquals("hello new_text world", r2.rows.get(0).get("content"));

        // Verify id 2 unchanged
        QueryResult r3 = engine.execute("SELECT content FROM articles WHERE id = 2;");
        assertTrue(r3.message, r3.success);
        assertEquals("sample text without match", r3.rows.get(0).get("content"));

        // Test UPDATE with arithmetic expression
        QueryResult r4 = engine.execute("UPDATE articles SET views = views + 5 WHERE id = 1;");
        assertTrue(r4.message, r4.success);
        QueryResult r5 = engine.execute("SELECT views FROM articles WHERE id = 1;");
        assertTrue(r5.message, r5.success);
        assertEquals(15L, ((Number) r5.rows.get(0).get("views")).longValue());

        // Test unknown column in WHERE clause error
        QueryResult rUnknown = engine.execute("UPDATE articles SET content = 'test' WHERE invalid_col = 1;");
        assertFalse(rUnknown.success);
        assertTrue(rUnknown.message, rUnknown.message.contains("ERROR 1054 (42S22): Unknown column 'invalid_col' in 'where clause'"));

        // Test unknown database error
        QueryResult rDb = engine.execute("USE non_existing_db;");
        assertFalse(rDb.success);
        assertEquals("ERROR 1049 (42000): Unknown database 'non_existing_db'", rDb.message);

        engine.execute("DROP DATABASE update_test_db;");
    }
}

