package com.mysql.pocketsql;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.mysql.pocketsql.engine.SecurityHelper;
import com.mysql.pocketsql.engine.StorageEngine;

import static org.junit.Assert.*;

public class SecurityUnitTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testEncryptionDecryption() throws Exception {
        String original = "Hello, PocketSQL Security!";
        String encrypted = SecurityHelper.encrypt(original);
        assertNotEquals(original, encrypted);
        
        String decrypted = SecurityHelper.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testStorageEngineEncryption() throws Exception {
        File baseDir = tempFolder.newFolder("pocketsql_base");
        StorageEngine storageEngine = new StorageEngine(baseDir);

        // Write a table row
        String dbName = "testdb";
        String tableName = "users";
        storageEngine.createDatabaseDir(dbName);

        JSONArray rows = new JSONArray();
        JSONObject row = new JSONObject();
        row.put("id", 1);
        row.put("name", "Alice");
        rows.put(row);

        storageEngine.writeTableRows(dbName, tableName, rows);

        // Check if the written file on disk is encrypted (i.e. not valid JSON plaintext)
        File tableFile = new File(new File(new File(baseDir, "databases"), dbName), tableName + ".pqsql");
        assertTrue(tableFile.exists());
        
        String rawContent = new String(Files.readAllBytes(tableFile.toPath()), StandardCharsets.UTF_8);
        // It shouldn't be parseable as JSONArray because it is encrypted
        try {
            new JSONArray(rawContent);
            fail("Expected exception since raw file content should be encrypted");
        } catch (Exception e) {
            // Expected
        }

        // Read through StorageEngine and verify it is decrypted
        JSONArray readRows = storageEngine.readTableRows(dbName, tableName);
        assertEquals(1, readRows.length());
        assertEquals("Alice", readRows.getJSONObject(0).getString("name"));
    }

    @Test
    public void testStorageEngineLegacyPlaintextFallback() throws Exception {
        File baseDir = tempFolder.newFolder("pocketsql_base_legacy");
        StorageEngine storageEngine = new StorageEngine(baseDir);

        String dbName = "legacy_db";
        String tableName = "legacy_table";
        storageEngine.createDatabaseDir(dbName);

        // Directly write raw plaintext JSON to the file
        File dbFolder = new File(new File(baseDir, "databases"), dbName);
        File tableFile = new File(dbFolder, tableName + ".pqsql");
        
        JSONArray rows = new JSONArray();
        JSONObject row = new JSONObject();
        row.put("id", 42);
        row.put("name", "Legacy User");
        rows.put(row);

        try (FileOutputStream fos = new FileOutputStream(tableFile);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(rows.toString());
        }

        // Read using StorageEngine - should fall back to raw content successfully
        JSONArray readRows = storageEngine.readTableRows(dbName, tableName);
        assertEquals(1, readRows.length());
        assertEquals("Legacy User", readRows.getJSONObject(0).getString("name"));

        // Rewrite it - it should now be encrypted
        storageEngine.writeTableRows(dbName, tableName, readRows);
        String rawContent = new String(Files.readAllBytes(tableFile.toPath()), StandardCharsets.UTF_8);
        try {
            new JSONArray(rawContent);
            fail("Expected exception since raw file content should now be encrypted");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testApiKeyManagerEncryption() throws Exception {
        File baseDir = tempFolder.newFolder("pocketsql_keys");
        com.mysql.pocketsql.engine.SqlApiKeyManager keyManager = new com.mysql.pocketsql.engine.SqlApiKeyManager(baseDir);
        String key = keyManager.generateKey("Test Label");
        
        // Assert that the apikeys.json file on disk is encrypted (i.e. not valid JSON plaintext)
        File keysFile = new File(baseDir, "apikeys.json");
        assertTrue(keysFile.exists());
        
        String rawContent = new String(Files.readAllBytes(keysFile.toPath()), StandardCharsets.UTF_8);
        try {
            new JSONObject(rawContent);
            fail("Expected exception since raw apikeys.json content should be encrypted");
        } catch (Exception e) {
            // Expected
        }
        
        // Reload keys manager and verify it decrypts and finds the key
        com.mysql.pocketsql.engine.SqlApiKeyManager reloaded = new com.mysql.pocketsql.engine.SqlApiKeyManager(baseDir);
        assertTrue(reloaded.isValidKey(key));
    }
}
