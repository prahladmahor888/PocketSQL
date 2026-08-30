package com.mysql.pocketsql;

import org.junit.Test;
import static org.junit.Assert.*;
import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.QueryResult;
import java.io.File;
import java.util.Map;

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
    
    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) deleteRecursive(c);
        }
        f.delete();
    }
}
