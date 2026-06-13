package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SqlApiServer {
    private ServerSocket serverSocket;
    private final DatabaseEngine engine;
    private final SqlApiKeyManager apiKeyManager;
    private final int port = 8080;
    private int activePort = 8080;
    private boolean isRunning = false;
    private String bindErrorMessage = null;
    private ExecutorService threadPool;
    private Thread serverThread;

    public SqlApiServer(DatabaseEngine engine, SqlApiKeyManager apiKeyManager) {
        this.engine = engine;
        this.apiKeyManager = apiKeyManager;
    }

    public synchronized void start() {
        if (isRunning) return;
        
        bindErrorMessage = null;
        int targetPort = port;
        boolean bound = false;
        while (!bound && targetPort < port + 20) {
            try {
                serverSocket = TlsServerHelper.getSslServerSocketFactory().createServerSocket(targetPort);
                ((javax.net.ssl.SSLServerSocket) serverSocket).setEnabledProtocols(new String[]{"TLSv1.3"});
                activePort = targetPort;
                bound = true;
            } catch (Exception e) {
                try {
                    serverSocket = new ServerSocket(targetPort);
                    activePort = targetPort;
                    bound = true;
                } catch (java.io.IOException ex) {
                    targetPort++;
                }
            }
        }
        if (!bound) {
            bindErrorMessage = "Ports 8080-8099 are already in use.";
            return;
        }

        isRunning = true;
        threadPool = Executors.newCachedThreadPool();
        serverThread = new Thread(this::runServerLoop);
        serverThread.start();
    }

    public synchronized void stop() {
        bindErrorMessage = null;
        if (!isRunning) return;
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
    }

    private void runServerLoop() {
        try {
            while (isRunning) {
                if (serverSocket == null || serverSocket.isClosed()) {
                    break;
                }
                Socket clientSocket = serverSocket.accept();
                if (!isRunning) {
                    try { clientSocket.close(); } catch(Exception e){}
                    break;
                }
                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (Exception e) {
            if (isRunning) {
                com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
            }
        }
    }

    private void handleClient(Socket socket) {
        try (InputStream is = socket.getInputStream();
             OutputStream os = socket.getOutputStream()) {

            // Read HTTP request line
            String requestLine = readLine(is);
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }

            String[] reqParts = requestLine.split(" ");
            if (reqParts.length < 2) {
                sendResponse(os, 400, "Bad Request", "{\"success\":false,\"message\":\"Bad Request\"}");
                return;
            }
            String method = reqParts[0];
            String path = reqParts[1];

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(is)) != null && !line.trim().isEmpty()) {
                int idx = line.indexOf(":");
                if (idx != -1) {
                    String name = line.substring(0, idx).trim().toLowerCase();
                    String val = line.substring(idx + 1).trim();
                    headers.put(name, val);
                }
            }

            // CORS Preflight Options check
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(os, 204, "No Content", "");
                return;
            }

            if (!"/api/query".equals(path)) {
                sendResponse(os, 404, "Not Found", "{\"success\":false,\"message\":\"Not Found\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(method)) {
                sendResponse(os, 405, "Method Not Allowed", "{\"success\":false,\"message\":\"Method Not Allowed. Use POST.\"}");
                return;
            }

            // Bearer Token Check
            String authHeader = headers.get("authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendResponse(os, 401, "Unauthorized", "{\"success\":false,\"message\":\"Unauthorized: Missing Bearer token.\"}");
                return;
            }
            String token = authHeader.substring(7).trim();
            if (!apiKeyManager.isValidKey(token)) {
                sendResponse(os, 403, "Forbidden", "{\"success\":false,\"message\":\"Forbidden: Invalid API Key.\"}");
                return;
            }

            // Content length body reading
            String contentLengthStr = headers.get("content-length");
            int contentLength = 0;
            if (contentLengthStr != null) {
                try {
                    contentLength = Integer.parseInt(contentLengthStr);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            byte[] bodyBytes = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                int count = is.read(bodyBytes, read, contentLength - read);
                if (count == -1) break;
                read += count;
            }
            String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);

            String sql = null;
            String db = null;
            try {
                if (!bodyStr.trim().isEmpty()) {
                    JSONObject bodyObj = new JSONObject(bodyStr);
                    sql = bodyObj.optString("sql");
                    db = bodyObj.optString("database", null);
                }
            } catch (Exception e) {
                sendResponse(os, 400, "Bad Request", "{\"success\":false,\"message\":\"Bad Request: Invalid JSON.\"}");
                return;
            }

            if (sql == null || sql.trim().isEmpty()) {
                sendResponse(os, 400, "Bad Request", "{\"success\":false,\"message\":\"Bad Request: Missing 'sql' parameter.\"}");
                return;
            }

            try {
                QueryResult result;
                synchronized (engine) {
                    String originalDb = engine.getActiveDatabase();
                    String originalUser = engine.getCurrentUser();
                    String originalHost = engine.getCurrentHost();
                    try {
                        engine.setCurrentUser(SecurityHelper.getDefaultUser(), SecurityHelper.getDefaultHost());
                        if (db != null && !db.trim().isEmpty()) {
                            engine.useDatabase(db.trim());
                        }
                        result = engine.execute(sql);
                    } finally {
                        engine.setCurrentUser(originalUser, originalHost);
                        if (originalDb != null) {
                            try {
                                engine.useDatabase(originalDb);
                            } catch (Exception e) {
                                // Ignore
                            }
                        } else {
                            engine.activeDatabaseName = null;
                            engine.activeSchemaJson = null;
                        }
                    }
                }

                JSONObject resObj = toJSON(result);
                sendResponse(os, 200, "OK", resObj.toString());
            } catch (Exception e) {
                sendResponse(os, 500, "Internal Server Error", "{\"success\":false,\"message\":\"Internal Server Error: " + e.getMessage() + "\"}");
            }

        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private String readLine(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                bos.write(b);
            }
        }
        if (bos.size() == 0 && b == -1) {
            return null;
        }
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private void sendResponse(OutputStream os, int statusCode, String statusText, String responseBody) throws Exception {
        byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));
        os.write(bodyBytes);
        os.flush();
    }

    private JSONObject toJSON(QueryResult result) {
        JSONObject json = new JSONObject();
        try {
            json.put("success", result.success);
            json.put("message", result.message);
            json.put("affectedRows", result.affectedRows);
            json.put("executionTimeMs", result.executionTimeMs);

            if (result.columns != null) {
                JSONArray cols = new JSONArray();
                for (String c : result.columns) {
                    cols.put(c);
                }
                json.put("columns", cols);
            }

            if (result.columnTypes != null) {
                JSONArray types = new JSONArray();
                for (String t : result.columnTypes) {
                    types.put(t);
                }
                json.put("columnTypes", types);
            }

            if (result.rows != null) {
                JSONArray rowsArr = new JSONArray();
                for (Map<String, Object> r : result.rows) {
                    JSONObject rowObj = new JSONObject();
                    for (Map.Entry<String, Object> entry : r.entrySet()) {
                        rowObj.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
                    }
                    rowsArr.put(rowObj);
                }
                json.put("rows", rowsArr);
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
        return json;
    }

    public boolean isRunning() {
        return isRunning && bindErrorMessage == null;
    }

    public int getPort() {
        return port;
    }

    public int getActivePort() {
        return activePort;
    }

    public String getBindErrorMessage() {
        return bindErrorMessage;
    }

    public boolean isTlsEnabled() {
        return serverSocket instanceof javax.net.ssl.SSLServerSocket;
    }
}
