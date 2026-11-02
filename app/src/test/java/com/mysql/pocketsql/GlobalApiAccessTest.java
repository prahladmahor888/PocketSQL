package com.mysql.pocketsql;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.SqlApiKeyManager;
import com.mysql.pocketsql.engine.SqlApiServer;
import com.mysql.pocketsql.engine.SqlTunnelClient;

import static org.junit.Assert.*;

public class GlobalApiAccessTest {

    private DatabaseEngine engine;
    private SqlApiKeyManager apiKeyManager;
    private SqlApiServer apiServer;
    private File testDir;
    private String testKey;

    @Before
    public void setUp() {
        testDir = new File("build/test-global-api");
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        testDir.mkdirs();

        engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");

        apiKeyManager = new SqlApiKeyManager(testDir);
        testKey = apiKeyManager.generateKey("Test Global Key", "*");

        apiServer = new SqlApiServer(engine, apiKeyManager);
    }

    @After
    public void tearDown() {
        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory() && f.listFiles() != null) {
            for (File c : f.listFiles()) {
                deleteRecursive(c);
            }
        }
        f.delete();
    }

    @Test
    public void testPingEndpointForGlobalReachability() throws Exception {
        String request = "GET /api/ping HTTP/1.1\r\n" +
                "Host: pocketsql-global.loca.lt\r\n" +
                "User-Agent: curl/7.68.0\r\n" +
                "\r\n";

        ByteArrayInputStream is = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        apiServer.handleHttpRequest(is, os);

        String response = os.toString(StandardCharsets.UTF_8.name());
        assertTrue("Response must contain 200 OK", response.contains("HTTP/1.1 200 OK"));
        assertTrue("Response must contain CORS header", response.contains("Access-Control-Allow-Origin: *"));
        assertTrue("Response must contain online message", response.contains("PocketSQL API Server is Online"));
    }

    @Test
    public void testCorsPreflightOptions() throws Exception {
        String request = "OPTIONS /api/query HTTP/1.1\r\n" +
                "Host: api.mycustomdomain.com\r\n" +
                "Origin: https://dashboard.mycustomdomain.com\r\n" +
                "\r\n";

        ByteArrayInputStream is = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        apiServer.handleHttpRequest(is, os);

        String response = os.toString(StandardCharsets.UTF_8.name());
        assertTrue("Response must contain 204 No Content", response.contains("HTTP/1.1 204 No Content"));
        assertTrue("Response must contain CORS header", response.contains("Access-Control-Allow-Origin: *"));
    }

    @Test
    public void testQueryExecutionViaGlobalHttpHandler() throws Exception {
        // Create database and table first
        engine.execute("CREATE DATABASE testdb;");
        engine.execute("USE testdb;");
        engine.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50));");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob');");

        String body = "{\"sql\":\"SELECT * FROM users ORDER BY id ASC;\",\"database\":\"testdb\"}";
        String request = "POST /api/query HTTP/1.1\r\n" +
                "Host: api.mycustomdomain.com\r\n" +
                "Authorization: Bearer " + testKey + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "\r\n" +
                body;

        ByteArrayInputStream is = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        apiServer.handleHttpRequest(is, os);

        String response = os.toString(StandardCharsets.UTF_8.name());
        assertTrue("Response must contain 200 OK", response.contains("HTTP/1.1 200 OK"));

        int jsonStart = response.indexOf("\r\n\r\n") + 4;
        String jsonStr = response.substring(jsonStart);
        JSONObject json = new JSONObject(jsonStr);

        assertTrue("Success flag must be true", json.optBoolean("success"));
        assertEquals("Must return 2 rows", 2, json.optJSONArray("rows").length());
        assertEquals("Alice", json.optJSONArray("rows").getJSONObject(0).optString("name"));
        assertEquals("Bob", json.optJSONArray("rows").getJSONObject(1).optString("name"));
    }

    @Test
    public void testUnauthorizedWhenMissingBearerToken() throws Exception {
        String body = "{\"sql\":\"SHOW DATABASES;\"}";
        String request = "POST /api/query HTTP/1.1\r\n" +
                "Host: pocketsql-tunnel.loca.lt\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "\r\n" +
                body;

        ByteArrayInputStream is = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        apiServer.handleHttpRequest(is, os);

        String response = os.toString(StandardCharsets.UTF_8.name());
        assertTrue("Response must contain 401 Unauthorized", response.contains("HTTP/1.1 401 Unauthorized"));
    }

    @Test
    public void testForbiddenWhenInvalidBearerToken() throws Exception {
        String body = "{\"sql\":\"SHOW DATABASES;\"}";
        String request = "POST /api/query HTTP/1.1\r\n" +
                "Host: pocketsql-tunnel.loca.lt\r\n" +
                "Authorization: Bearer invalid_token_12345\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "\r\n" +
                body;

        ByteArrayInputStream is = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        apiServer.handleHttpRequest(is, os);

        String response = os.toString(StandardCharsets.UTF_8.name());
        assertTrue("Response must contain 403 Forbidden", response.contains("HTTP/1.1 403 Forbidden"));
    }

    @Test
    public void testNotFoundForInvalidRoute() throws Exception {
        String request = "GET /unknown/route HTTP/1.1\r\n" +
                "Host: pocketsql.customdomain.org\r\n" +
                "\r\n";

        ByteArrayInputStream is = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        apiServer.handleHttpRequest(is, os);

        String response = os.toString(StandardCharsets.UTF_8.name());
        assertTrue("Response must contain 404 Not Found", response.contains("HTTP/1.1 404 Not Found"));
    }

    @Test
    public void testSqlTunnelClientLifecycle() {
        SqlTunnelClient client = new SqlTunnelClient(
                apiServer,
                "https://loca.lt",
                "my-subdomain",
                "api.mycustomdomain.com",
                4,
                null
        );

        assertFalse("Client initially stopped", client.isRunning());
        client.stop();
        assertFalse("Client remains stopped after stop call", client.isRunning());
    }
}

