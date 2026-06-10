package com.mysql.pocketsql;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.util.Map;
import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.QueryResult;

import static org.junit.Assert.*;

public class SystemDatabasesUnitTest {

    private DatabaseEngine engine;
    private File testDir;

    @Before
    public void setUp() {
        testDir = new File("build/test-pocketsql-sys");
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
    public void testShowDatabasesIncludesSystemDbs() {
        QueryResult r = engine.execute("SHOW DATABASES;");
        assertTrue(r.success);
        
        boolean hasInfoSchema = false;
        boolean hasPocketSql = false;
        boolean hasSys = false;
        
        for (Map<String, Object> row : r.rows) {
            String db = (String) row.get("Database");
            if ("information_schema".equalsIgnoreCase(db)) hasInfoSchema = true;
            if ("pocketsql".equalsIgnoreCase(db)) hasPocketSql = true;
            if ("sys".equalsIgnoreCase(db)) hasSys = true;
        }
        
        assertTrue("Should contain information_schema", hasInfoSchema);
        assertTrue("Should contain pocketsql", hasPocketSql);
        assertTrue("Should contain sys", hasSys);
    }

    @Test
    public void testPocketSqlDatabaseTables() {
        QueryResult r = engine.execute("USE pocketsql;");
        assertTrue(r.success);

        QueryResult tables = engine.execute("SHOW TABLES;");
        assertTrue(tables.success);
        
        boolean hasUser = false;
        boolean hasDb = false;
        for (Map<String, Object> row : tables.rows) {
            String tbl = (String) row.get("Tables_in_pocketsql");
            if ("user".equalsIgnoreCase(tbl)) hasUser = true;
            if ("db".equalsIgnoreCase(tbl)) hasDb = true;
        }
        assertTrue("Should contain user table", hasUser);
        assertTrue("Should contain db table", hasDb);

        // Query user table
        QueryResult users = engine.execute("SELECT * FROM user;");
        assertTrue(users.success);
        assertTrue(users.rows.size() >= 1);
        assertEquals("root", users.rows.get(0).get("User"));
        assertEquals("localhost", users.rows.get(0).get("Host"));
        assertEquals("Y", users.rows.get(0).get("Select_priv"));
    }

    @Test
    public void testInformationSchemaDatabaseTables() {
        QueryResult r = engine.execute("USE information_schema;");
        assertTrue(r.success);

        QueryResult tables = engine.execute("SHOW TABLES;");
        assertTrue(tables.success);
        
        boolean hasSchemata = false;
        boolean hasTables = false;
        boolean hasColumns = false;
        
        for (Map<String, Object> row : tables.rows) {
            String tbl = (String) row.get("Tables_in_information_schema");
            if ("SCHEMATA".equalsIgnoreCase(tbl)) hasSchemata = true;
            if ("TABLES".equalsIgnoreCase(tbl)) hasTables = true;
            if ("COLUMNS".equalsIgnoreCase(tbl)) hasColumns = true;
        }
        assertTrue("Should contain SCHEMATA", hasSchemata);
        assertTrue("Should contain TABLES", hasTables);
        assertTrue("Should contain COLUMNS", hasColumns);

        // Query schemata
        QueryResult schemata = engine.execute("SELECT * FROM SCHEMATA;");
        assertTrue(schemata.success);
        assertTrue(schemata.rows.size() >= 3); // sys, pocketsql, information_schema
    }

    @Test
    public void testSysDatabaseTables() {
        QueryResult r = engine.execute("USE sys;");
        assertTrue(r.success);

        QueryResult tables = engine.execute("SHOW TABLES;");
        assertTrue(tables.success);
        
        boolean hasConfig = false;
        boolean hasVersion = false;
        for (Map<String, Object> row : tables.rows) {
            String tbl = (String) row.get("Tables_in_sys");
            if ("sys_config".equalsIgnoreCase(tbl)) hasConfig = true;
            if ("version".equalsIgnoreCase(tbl)) hasVersion = true;
        }
        assertTrue("Should contain sys_config", hasConfig);
        assertTrue("Should contain version", hasVersion);

        // Query version
        QueryResult version = engine.execute("SELECT * FROM version;");
        assertTrue(version.success);
        assertEquals("8.0.25", version.rows.get(0).get("version"));
        assertEquals("PocketSQL", version.rows.get(0).get("source"));
    }

    @Test
    public void testPocketSqlAll38Tables() {
        QueryResult r = engine.execute("USE pocketsql;");
        assertTrue(r.success);

        QueryResult tables = engine.execute("SHOW TABLES;");
        assertTrue(tables.success);

        String[] expectedTables = {
            "columns_priv", "component", "db", "default_roles", "engine_cost", "func",
            "general_log", "global_grants", "gtid_executed", "help_category",
            "help_keyword", "help_relation", "help_topic", "innodb_index_stats",
            "innodb_table_stats", "ndb_binlog_index", "password_history", "plugin",
            "procs_priv", "proxies_priv", "replication_asynchronous_connection_failover",
            "replication_asynchronous_connection_failover_managed", "replication_group_configuration_version",
            "replication_group_member_actions", "role_edges", "server_cost", "servers",
            "slave_master_info", "slave_relay_log_info", "slave_worker_info", "slow_log",
            "tables_priv", "time_zone", "time_zone_leap_second", "time_zone_name",
            "time_zone_transition", "time_zone_transition_type", "user"
        };

        assertEquals(38, tables.rows.size());

        for (String expectedTbl : expectedTables) {
            boolean found = false;
            for (Map<String, Object> row : tables.rows) {
                String tbl = (String) row.get("Tables_in_pocketsql");
                if (expectedTbl.equalsIgnoreCase(tbl)) {
                    found = true;
                    break;
                }
            }
            assertTrue("Should contain table: " + expectedTbl, found);

            // Query each of the 38 tables to ensure they execute successfully
            QueryResult tblQuery = engine.execute("SELECT * FROM " + expectedTbl + ";");
            assertTrue("Querying " + expectedTbl + " should succeed", tblQuery.success);
        }
    }

    @Test
    public void testShowTablesInTargetDatabase() {
        engine.execute("CREATE DATABASE test_db_show;");
        engine.execute("USE test_db_show;");
        engine.execute("CREATE TABLE t1(id INT);");

        QueryResult r = engine.execute("SHOW TABLES IN information_schema;");
        assertTrue(r.success);
        assertEquals(79, r.rows.size());
        assertEquals("Tables_in_information_schema", r.columns.get(0));

        QueryResult r2 = engine.execute("SHOW TABLES IN sys;");
        assertTrue(r2.success);
        assertEquals(101, r2.rows.size());
        assertEquals("Tables_in_sys", r2.columns.get(0));

        QueryResult r3 = engine.execute("SHOW TABLES IN pocketsql;");
        assertTrue(r3.success);
        assertEquals(38, r3.rows.size());
        assertEquals("Tables_in_pocketsql", r3.columns.get(0));

        QueryResult r4 = engine.execute("SHOW TABLES FROM test_db_show;");
        assertTrue(r4.success);
        assertEquals(1, r4.rows.size());
        assertEquals("t1", r4.rows.get(0).get("Tables_in_test_db_show"));
    }

    @Test
    public void testSystemDatabasesConstraintsMetadata() {
        engine.execute("CREATE DATABASE test_db_constraints;");
        engine.execute("USE test_db_constraints;");
        engine.execute("CREATE TABLE parent_t(id INT PRIMARY KEY);");
        engine.execute("CREATE TABLE child_t(id INT PRIMARY KEY, parent_id INT, FOREIGN KEY (parent_id) REFERENCES parent_t(id));");

        QueryResult pkKcu = engine.execute("SELECT * FROM information_schema.key_column_usage WHERE TABLE_SCHEMA='test_db_constraints';");
        assertTrue(pkKcu.success);
        assertEquals(3, pkKcu.rows.size());

        QueryResult tc = engine.execute("SELECT * FROM information_schema.table_constraints WHERE TABLE_SCHEMA='test_db_constraints';");
        assertTrue(tc.success);
        assertEquals(3, tc.rows.size());

        QueryResult rc = engine.execute("SELECT * FROM information_schema.referential_constraints WHERE CONSTRAINT_SCHEMA='test_db_constraints';");
        assertTrue(rc.success);
        assertEquals(1, rc.rows.size());
        assertEquals("child_t", rc.rows.get(0).get("TABLE_NAME"));
        assertEquals("parent_t", rc.rows.get(0).get("REFERENCED_TABLE_NAME"));
    }

    @Test
    public void testSystemSchemasPersistedAndDescribe() throws Exception {
        File databasesDir = new File(testDir, "databases");
        File infoSchemaDir = new File(databasesDir, "information_schema");
        File pocketsqlDir = new File(databasesDir, "pocketsql");
        File sysDir = new File(databasesDir, "sys");

        assertTrue("information_schema directory should exist", infoSchemaDir.exists());
        assertTrue("pocketsql directory should exist", pocketsqlDir.exists());
        assertTrue("sys directory should exist", sysDir.exists());

        File infoSchemaJson = new File(infoSchemaDir, "schema.json");
        File pocketsqlJson = new File(pocketsqlDir, "schema.json");
        File sysJson = new File(sysDir, "schema.json");
        File usersJson = new File(testDir, "users.json");

        assertTrue("information_schema.json should exist", infoSchemaJson.exists());
        assertTrue("pocketsql.json should exist", pocketsqlJson.exists());
        assertTrue("sys.json should exist", sysJson.exists());
        assertTrue("users.json should exist", usersJson.exists());

        // Assert that they are encrypted at rest (i.e. not parseable as raw JSON)
        try {
            String rawUsers = new String(java.nio.file.Files.readAllBytes(usersJson.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            new org.json.JSONObject(rawUsers);
            fail("users.json is stored as plaintext JSON!");
        } catch (org.json.JSONException e) {
            // Expected encryption
        }

        // Assert that they are encrypted at rest (i.e. not parseable as raw JSON)
        try {
            String rawInfo = new String(java.nio.file.Files.readAllBytes(infoSchemaJson.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            new org.json.JSONObject(rawInfo);
            fail("information_schema.json is stored as plaintext JSON!");
        } catch (org.json.JSONException e) {
            // Expected encryption
        }

        try {
            String rawPocket = new String(java.nio.file.Files.readAllBytes(pocketsqlJson.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            new org.json.JSONObject(rawPocket);
            fail("pocketsql.json is stored as plaintext JSON!");
        } catch (org.json.JSONException e) {
            // Expected encryption
        }

        try {
            String rawSys = new String(java.nio.file.Files.readAllBytes(sysJson.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            new org.json.JSONObject(rawSys);
            fail("sys.json is stored as plaintext JSON!");
        } catch (org.json.JSONException e) {
            // Expected encryption
        }

        engine.execute("USE pocketsql;");
        QueryResult describeUser = engine.execute("DESCRIBE user;");
        assertTrue("DESCRIBE user should succeed: " + describeUser.message, describeUser.success);
        assertEquals("pocketsql.user table should have exactly 51 columns", 51, describeUser.rows.size());
        
        boolean hasHostCol = false;
        boolean hasUserCol = false;
        boolean hasSslType = false;
        boolean hasMaxQuestions = false;
        
        for (Map<String, Object> row : describeUser.rows) {
            String field = (String) row.get("Field");
            if ("Host".equalsIgnoreCase(field)) hasHostCol = true;
            if ("User".equalsIgnoreCase(field)) hasUserCol = true;
            if ("ssl_type".equalsIgnoreCase(field)) hasSslType = true;
            if ("max_questions".equalsIgnoreCase(field)) hasMaxQuestions = true;
        }
        assertTrue("DESCRIBE user should contain Host column", hasHostCol);
        assertTrue("DESCRIBE user should contain User column", hasUserCol);
        assertTrue("DESCRIBE user should contain ssl_type column", hasSslType);
        assertTrue("DESCRIBE user should contain max_questions column", hasMaxQuestions);

        QueryResult showColumns = engine.execute("SHOW COLUMNS FROM user;");
        assertTrue("SHOW COLUMNS FROM user should succeed", showColumns.success);
        assertEquals(describeUser.rows.size(), showColumns.rows.size());

        QueryResult selectUser = engine.execute("SELECT * FROM user;");
        assertTrue("SELECT * FROM user should succeed", selectUser.success);
        assertTrue(selectUser.rows.size() >= 1);
        Map<String, Object> rootRow = selectUser.rows.get(0);
        assertEquals("caching_sha2_password", rootRow.get("plugin"));
        assertEquals("N", rootRow.get("password_expired"));
        assertEquals(0L, ((Number)rootRow.get("max_questions")).longValue());
    }

    @Test
    public void testProperSystemTableSchemasAndFallbacks() {
        engine.execute("USE information_schema;");
        QueryResult describeEngines = engine.execute("DESCRIBE engines;");
        assertTrue(describeEngines.success);
        boolean hasSupport = false;
        boolean hasTransactions = false;
        for (Map<String, Object> row : describeEngines.rows) {
            String field = (String) row.get("Field");
            if ("SUPPORT".equalsIgnoreCase(field)) hasSupport = true;
            if ("TRANSACTIONS".equalsIgnoreCase(field)) hasTransactions = true;
        }
        assertTrue("DESCRIBE engines should contain SUPPORT", hasSupport);
        assertTrue("DESCRIBE engines should contain TRANSACTIONS", hasTransactions);

        QueryResult describeInnodb = engine.execute("DESCRIBE INNODB_BUFFER_PAGE;");
        assertTrue(describeInnodb.success);
        boolean hasPages = false;
        for (Map<String, Object> row : describeInnodb.rows) {
            String field = (String) row.get("Field");
            if ("number_of_pages".equalsIgnoreCase(field)) hasPages = true;
        }
        assertTrue("DESCRIBE INNODB_BUFFER_PAGE should contain number_of_pages", hasPages);

        engine.execute("USE sys;");
        QueryResult describeSummary = engine.execute("DESCRIBE host_summary;");
        assertTrue(describeSummary.success);
        boolean hasLatency = false;
        for (Map<String, Object> row : describeSummary.rows) {
            String field = (String) row.get("Field");
            if ("statement_latency".equalsIgnoreCase(field)) hasLatency = true;
        }
        assertTrue("DESCRIBE host_summary should contain statement_latency", hasLatency);

        // Verify system table primary keys show up in constraints/KCU metadata
        QueryResult userPkKcu = engine.execute("SELECT * FROM information_schema.key_column_usage WHERE TABLE_SCHEMA='pocketsql' AND TABLE_NAME='user';");
        assertTrue(userPkKcu.success);
        assertEquals(2, userPkKcu.rows.size()); // Host, User

        QueryResult userPkTc = engine.execute("SELECT * FROM information_schema.table_constraints WHERE TABLE_SCHEMA='pocketsql' AND TABLE_NAME='user';");
        assertTrue(userPkTc.success);
        assertEquals(1, userPkTc.rows.size()); // PRIMARY KEY

        // Verify sys.host_summary returns correct live rows
        QueryResult selectHostSummary = engine.execute("SELECT * FROM sys.host_summary;");
        assertTrue(selectHostSummary.success);
        assertEquals(1, selectHostSummary.rows.size());
        assertEquals("localhost", selectHostSummary.rows.get(0).get("host"));
        assertEquals("15.27 ms", selectHostSummary.rows.get(0).get("file_io_latency"));

        // Verify sys.session_ssl_status returns correct live rows
        QueryResult selectSslStatus = engine.execute("SELECT * FROM sys.session_ssl_status;");
        assertTrue(selectSslStatus.success);
        assertEquals(1, selectSslStatus.rows.size());
        assertEquals("TLSv1.3", selectSslStatus.rows.get(0).get("ssl_version"));
        assertEquals("TLS_AES_256_GCM_SHA384", selectSslStatus.rows.get(0).get("ssl_cipher"));
    }

    @Test
    public void testSystemDatabaseExportImportSync() throws Exception {
        // Initially, only root@localhost exists
        QueryResult r1 = engine.execute("SELECT * FROM pocketsql.user;");
        assertTrue(r1.success);
        int initialUserCount = r1.rows.size();
        
        // Create the school database
        QueryResult rDb = engine.execute("CREATE DATABASE school;");
        assertTrue(rDb.message, rDb.success);

        // Add a new user with some privileges
        QueryResult r2 = engine.execute("CREATE USER 'backup_user'@'localhost' IDENTIFIED BY 'pass123';");
        assertTrue(r2.message, r2.success);
        
        QueryResult r3 = engine.execute("GRANT SELECT, INSERT ON school.* TO 'backup_user'@'localhost';");
        assertTrue(r3.message, r3.success);
        
        // Export the pocketsql database to a ZIP backup
        File backupFile = new File(testDir, "pocketsql_backup.zip");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(backupFile)) {
            com.mysql.pocketsql.engine.DatabaseExporter.exportDatabase(engine, "pocketsql", fos, "zip");
        }
        assertTrue(backupFile.exists());
        
        // Now drop the user to verify the restore works
        QueryResult r4 = engine.execute("DROP USER 'backup_user'@'localhost';");
        assertTrue(r4.success);
        
        // Verify user is gone
        QueryResult r5 = engine.execute("SELECT * FROM pocketsql.user;");
        assertTrue(r5.success);
        assertEquals(initialUserCount, r5.rows.size());
        
        // Import the pocketsql database zip back
        try (java.io.FileInputStream fis = new java.io.FileInputStream(backupFile)) {
            com.mysql.pocketsql.engine.DatabaseExporter.importDatabase(engine, "pocketsql", fis, "zip");
        }
        
        // Verify that 'backup_user'@'localhost' is restored with correct privileges!
        QueryResult r6 = engine.execute("SELECT * FROM pocketsql.user;");
        assertTrue(r6.success);
        assertEquals(initialUserCount + 1, r6.rows.size());
        
        boolean foundRestored = false;
        for (Map<String, Object> row : r6.rows) {
            if ("backup_user".equals(row.get("User"))) {
                foundRestored = true;
                break;
            }
        }
        assertTrue("Restored backup_user should be found", foundRestored);
        
        // Verify privileges are restored
        engine.setCurrentUser("backup_user", "localhost");
        QueryResult privCheck = engine.execute("USE school;");
        assertTrue("Restored user should be able to USE school database: " + privCheck.message, privCheck.success);
    }
}
