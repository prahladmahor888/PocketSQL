package com.mysql.pocketsql.engine;

import android.content.Context;

import com.mysql.pocketsql.SettingsManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SqlTunnelManager — Manages the lifecycle, state, listeners, and settings
 * for exposing the PocketSQL embedded API server globally over the internet.
 */
public class SqlTunnelManager {

    public enum TunnelState {
        STOPPED,
        CONNECTING,
        ONLINE,
        RECONNECTING,
        ERROR
    }

    public interface TunnelListener {
        void onStateChanged(TunnelState state, String statusMessage, String publicUrl);
    }

    private final Context context;
    private final SqlApiServer apiServer;
    private final SettingsManager settings;
    private final List<TunnelListener> listeners = new CopyOnWriteArrayList<>();

    private SqlTunnelClient client;
    private volatile TunnelState currentState = TunnelState.STOPPED;
    private volatile String currentPublicUrl = null;
    private volatile String currentMessage = "Global access is inactive";

    public SqlTunnelManager(Context context, SqlApiServer apiServer) {
        this.context = context.getApplicationContext();
        this.apiServer = apiServer;
        this.settings = new SettingsManager(context);
        this.currentPublicUrl = settings.getGlobalLastPublicUrl();
    }

    public synchronized void startTunnel() {
        if (client != null && client.isRunning()) {
            return;
        }

        int mode = settings.getGlobalTunnelMode();
        String server = settings.getGlobalCustomServer();
        String customDomain = "";
        String subdomain = "";

        if (mode == SettingsManager.TUNNEL_MODE_CUSTOM_DOMAIN) {
            customDomain = settings.getGlobalCustomDomain();
            subdomain = settings.getGlobalCustomSubdomain();
            if ((subdomain == null || subdomain.trim().isEmpty()) && customDomain != null && !customDomain.trim().isEmpty()) {
                String cleanDomain = customDomain.replace("https://", "").replace("http://", "").replaceAll("/.*", "");
                subdomain = cleanDomain.replace(".", "-").replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();
            }
        } else if (mode == SettingsManager.TUNNEL_MODE_CUSTOM_SUBDOMAIN) {
            subdomain = settings.getGlobalCustomSubdomain();
        }

        updateState(TunnelState.CONNECTING, "Connecting to global tunnel gateway...", currentPublicUrl);

        client = new SqlTunnelClient(
                apiServer,
                server,
                subdomain,
                customDomain,
                2,
                new SqlTunnelClient.TunnelListener() {
                    @Override
                    public void onConnected(String publicUrl, String assignedSubdomain) {
                        currentPublicUrl = publicUrl;
                        settings.setGlobalLastPublicUrl(publicUrl);
                        updateState(TunnelState.ONLINE, "Online (HTTPS)", publicUrl);
                    }

                    @Override
                    public void onReconnecting(String message, int retrySeconds) {
                        updateState(TunnelState.RECONNECTING, "Reconnecting in " + retrySeconds + "s...", currentPublicUrl);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        updateState(TunnelState.ERROR, "Tunnel error: " + errorMessage, currentPublicUrl);
                    }

                    @Override
                    public void onStopped() {
                        updateState(TunnelState.STOPPED, "Global access stopped", null);
                    }
                }
        );

        client.start();
    }

    public synchronized void stopTunnel() {
        if (client != null) {
            client.stop();
            client = null;
        }
        updateState(TunnelState.STOPPED, "Global access is inactive", null);
    }

    public synchronized void restartTunnel() {
        stopTunnel();
        startTunnel();
    }

    public synchronized void updateConfiguration(int mode, String domain, String subdomain, String server) {
        settings.setGlobalTunnelMode(mode);
        settings.setGlobalCustomDomain(domain);
        settings.setGlobalCustomSubdomain(subdomain);
        if (server != null && !server.trim().isEmpty()) {
            settings.setGlobalCustomServer(server);
        }
        if (settings.isGlobalAccessEnabled()) {
            restartTunnel();
        }
    }

    public boolean isTunnelActive() {
        return currentState == TunnelState.ONLINE;
    }

    public TunnelState getState() {
        return currentState;
    }

    public String getPublicUrl() {
        return currentPublicUrl;
    }

    public String getStatusMessage() {
        return currentMessage;
    }

    public void addListener(TunnelListener listener) {
        if (listener != null) {
            listeners.add(listener);
            listener.onStateChanged(currentState, currentMessage, currentPublicUrl);
        }
    }

    public void removeListener(TunnelListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void updateState(TunnelState newState, String message, String publicUrl) {
        this.currentState = newState;
        this.currentMessage = message;
        this.currentPublicUrl = publicUrl;

        SqlThreadScheduler.runOnMainThread(() -> {
            for (TunnelListener l : listeners) {
                try {
                    l.onStateChanged(newState, message, publicUrl);
                } catch (Exception e) {
                    SqlLog.printStackTrace(e);
                }
            }
        });
    }

    public void testGlobalConnection(SqlTunnelClient.TestCallback callback) {
        String urlToTest = currentPublicUrl != null ? currentPublicUrl : settings.getGlobalLastPublicUrl();
        if (urlToTest == null || urlToTest.isEmpty()) {
            callback.onFailure("No active Global URL to test. Please start the tunnel first.");
            return;
        }
        SqlTunnelClient.testGlobalConnectivity(urlToTest, callback);
    }
}

