package com.mysql.pocketsql.engine;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SqlTunnelClient — Reverse proxy tunnel client for PocketSQL.
 * Enables the local embedded SQL API server to be accessed globally over the internet,
 * supporting both automatic public URLs (without custom domain) and custom domains/servers.
 *
 * Designed with strictly bounded thread pools and persistent worker loops to eliminate
 * any possibility of thread exhaustion or stack memory allocation crashes on Android.
 */
public class SqlTunnelClient {

    public interface TunnelListener {
        void onConnected(String publicUrl, String assignedSubdomain);
        void onReconnecting(String message, int retrySeconds);
        void onError(String errorMessage);
        void onStopped();
    }

    private final SqlApiServer apiServer;
    private final String serverBaseUrl;
    private final String requestedSubdomain;
    private final String customDomain;
    private final int poolSize;
    private final TunnelListener listener;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger currentSessionId = new AtomicInteger(0);

    private Thread supervisorThread;
    private ExecutorService workerPool;
    private final Set<Socket> activeSockets = Collections.synchronizedSet(new HashSet<>());

    private volatile String activePublicUrl = null;
    private volatile String activeSubdomain = null;

    public SqlTunnelClient(SqlApiServer apiServer,
                           String serverBaseUrl,
                           String requestedSubdomain,
                           String customDomain,
                           int poolSize,
                           TunnelListener listener) {
        this.apiServer = apiServer;
        this.serverBaseUrl = (serverBaseUrl != null && !serverBaseUrl.trim().isEmpty())
                ? serverBaseUrl.trim().replaceAll("/+$", "")
                : "https://loca.lt";
        this.requestedSubdomain = (requestedSubdomain != null) ? requestedSubdomain.trim() : "";
        this.customDomain = (customDomain != null) ? customDomain.trim() : "";
        // Strict bounds: 1 to 3 workers max to protect mobile battery and memory
        this.poolSize = Math.max(1, Math.min(poolSize, 3));
        this.listener = listener;
    }

    public synchronized void start() {
        if (isRunning.get()) return;
        isRunning.set(true);

        final AtomicInteger threadNumber = new AtomicInteger(0);
        // Strictly fixed thread pool bounded to poolSize threads
        workerPool = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "PocketSQL-TunnelWorker-" + threadNumber.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        supervisorThread = new Thread(this::runSupervisorLoop, "PocketSQL-TunnelSupervisor");
        supervisorThread.setDaemon(true);
        supervisorThread.start();
    }

    public synchronized void stop() {
        if (!isRunning.get()) return;
        isRunning.set(false);
        currentSessionId.incrementAndGet();

        if (supervisorThread != null) {
            supervisorThread.interrupt();
            supervisorThread = null;
        }

        closeAllSockets();

        if (workerPool != null) {
            workerPool.shutdownNow();
            workerPool = null;
        }

        activePublicUrl = null;
        activeSubdomain = null;

        if (listener != null) {
            listener.onStopped();
        }
    }

    private void closeAllSockets() {
        synchronized (activeSockets) {
            for (Socket s : activeSockets) {
                try {
                    s.close();
                } catch (Exception ignored) {}
            }
            activeSockets.clear();
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public String getPublicUrl() {
        return activePublicUrl;
    }

    public String getSubdomain() {
        return activeSubdomain;
    }

    private void triggerReconnect() {
        currentSessionId.incrementAndGet();
        closeAllSockets();
        if (supervisorThread != null) {
            supervisorThread.interrupt();
        }
    }

    private void runSupervisorLoop() {
        int backoffSec = 3;

        while (isRunning.get()) {
            try {
                // 1. Request tunnel registration from the tunnel server
                TunnelRegistration registration = registerTunnel();
                if (registration == null) {
                    throw new Exception("Failed to register with tunnel server: " + serverBaseUrl);
                }

                activeSubdomain = registration.id;

                // Determine public URL:
                // If user specified custom domain, use it; otherwise use the assigned tunnel URL
                if (customDomain != null && !customDomain.isEmpty()) {
                    String domainWithProto = customDomain.startsWith("http://") || customDomain.startsWith("https://")
                            ? customDomain
                            : "https://" + customDomain;
                    activePublicUrl = domainWithProto.replaceAll("/+$", "");
                } else {
                    activePublicUrl = registration.url;
                }

                // Reset backoff on successful registration
                backoffSec = 3;

                if (listener != null) {
                    listener.onConnected(activePublicUrl, activeSubdomain);
                }

                // 2. Launch fixed worker threads for this session
                final int sessionId = currentSessionId.incrementAndGet();
                for (int i = 0; i < poolSize; i++) {
                    if (workerPool != null && !workerPool.isShutdown()) {
                        workerPool.execute(() -> runWorker(sessionId, registration.host, registration.port));
                    }
                }

                // 3. Keep supervisor alive and monitor session
                while (isRunning.get() && currentSessionId.get() == sessionId) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }

            } catch (InterruptedException ie) {
                if (!isRunning.get()) break;
            } catch (Exception e) {
                if (!isRunning.get()) break;

                SqlLog.err("SqlTunnelClient error: " + e.getMessage());
                closeAllSockets();

                if (listener != null) {
                    listener.onReconnecting("Tunnel disconnected: " + e.getMessage(), backoffSec);
                }

                try {
                    Thread.sleep(backoffSec * 1000L);
                } catch (InterruptedException ie) {
                    break;
                }

                // Exponential backoff up to 30 seconds
                backoffSec = Math.min(30, backoffSec * 2);
            }
        }
    }

    private void runWorker(final int sessionId, final String host, final int port) {
        int consecutiveFailures = 0;

        while (isRunning.get() && currentSessionId.get() == sessionId) {
            Socket remoteSocket = null;
            try {
                remoteSocket = new Socket();
                remoteSocket.connect(new InetSocketAddress(host, port), 10000);
                remoteSocket.setKeepAlive(true);
                remoteSocket.setTcpNoDelay(true);
                remoteSocket.setSoTimeout(0); // Wait for incoming HTTP request from gateway

                activeSockets.add(remoteSocket);
                consecutiveFailures = 0;

                InputStream is = remoteSocket.getInputStream();
                OutputStream os = remoteSocket.getOutputStream();

                // Wait for the first byte of incoming HTTP request from tunnel server
                int firstByte = is.read();
                if (firstByte == -1) {
                    // Gateway closed the connection
                    activeSockets.remove(remoteSocket);
                    try { remoteSocket.close(); } catch (Exception ignored) {}
                    continue;
                }

                // Push back the first byte so handleHttpRequest reads full HTTP request
                PushbackInputStream pbis = new PushbackInputStream(is, 1);
                pbis.unread(firstByte);

                // Process the HTTP request with the PocketSQL API Server!
                if (apiServer != null) {
                    apiServer.handleHttpRequest(pbis, os);
                }

            } catch (Exception e) {
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    // Remote endpoint unreachable or registration expired
                    triggerReconnect();
                    break;
                }
            } finally {
                if (remoteSocket != null) {
                    activeSockets.remove(remoteSocket);
                    try {
                        remoteSocket.close();
                    } catch (Exception ignored) {}
                }
            }

            // Pause briefly between socket connection cycles to prevent busy looping
            if (isRunning.get() && currentSessionId.get() == sessionId) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private static class TunnelRegistration {
        final String id;
        final String host;
        final int port;
        final String url;

        TunnelRegistration(String id, String host, int port, String url) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.url = url;
        }
    }

    private TunnelRegistration registerTunnel() throws Exception {
        String endpoint = serverBaseUrl + "/?new";
        if (requestedSubdomain != null && !requestedSubdomain.isEmpty()) {
            endpoint += "&subdomain=" + URLEncoder.encode(requestedSubdomain, "UTF-8");
        }

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "PocketSQL-Tunnel/1.0 (Android)");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Bypass-Tunnel-Reminder", "true");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);

        int code = conn.getResponseCode();
        if (code != 200) {
            // If custom subdomain was taken, retry once without subdomain
            if (code == 400 && requestedSubdomain != null && !requestedSubdomain.isEmpty()) {
                conn.disconnect();
                URL fallbackUrl = new URL(serverBaseUrl + "/?new");
                conn = (HttpURLConnection) fallbackUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "PocketSQL-Tunnel/1.0 (Android)");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Bypass-Tunnel-Reminder", "true");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);
                code = conn.getResponseCode();
            }
            if (code != 200) {
                throw new Exception("Server returned HTTP " + code);
            }
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            conn.disconnect();
        }

        JSONObject json = new JSONObject(sb.toString());
        String id = json.optString("id");
        int port = json.optInt("port");
        String assignedUrl = json.optString("url");

        if (port <= 0 || assignedUrl.isEmpty()) {
            throw new Exception("Invalid registration response: " + sb.toString());
        }

        // Parse host from serverBaseUrl
        String host = new URL(serverBaseUrl).getHost();
        return new TunnelRegistration(id, host, port, assignedUrl);
    }

    /**
     * Performs a live end-to-end HTTPS ping test against the public URL
     * to verify global internet connectivity and measure latency.
     */
    public static void testGlobalConnectivity(final String targetUrl, final TestCallback callback) {
        SqlThreadScheduler.runBackgroundTask(() -> {
            long start = System.currentTimeMillis();
            try {
                String testEndpoint = (targetUrl != null ? targetUrl.trim().replaceAll("/+$", "") : "") + "/api/ping";
                URL url = new URL(testEndpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "PocketSQL-Ping/1.0");
                conn.setRequestProperty("Bypass-Tunnel-Reminder", "true");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                long elapsed = System.currentTimeMillis() - start;

                if (code == 200 || code == 204) {
                    SqlThreadScheduler.runOnMainThread(() -> callback.onSuccess(elapsed));
                } else {
                    SqlThreadScheduler.runOnMainThread(() -> callback.onFailure("Server returned HTTP " + code));
                }
                conn.disconnect();
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                SqlThreadScheduler.runOnMainThread(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Connection timeout"));
            }
        });
    }

    public interface TestCallback {
        void onSuccess(long latencyMs);
        void onFailure(String error);
    }
}
