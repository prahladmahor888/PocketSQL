package com.mysql.pocketsql;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mysql.pocketsql.engine.SqlApiHelper;
import com.mysql.pocketsql.engine.SqlTunnelManager;

public class SqlApiService extends Service {
    private static final String CHANNEL_ID = "pocketsql_api_channel";
    private static final int NOTIFICATION_ID = 2026;
    public static final String ACTION_STOP_SERVER = "com.mysql.pocketsql.ACTION_STOP_SERVER";

    private SqlTunnelManager.TunnelListener tunnelListener;

    @Override
    public void onCreate() {
        super.onCreate();
        SqlApiHelper.init(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVER.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                stopForeground(true);
            } else {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.cancel(NOTIFICATION_ID);
                }
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start the local API server
        SqlApiHelper.getApiServer().start();

        // Create notification channel
        createNotificationChannel();

        // Check if global internet access tunnel is enabled
        SettingsManager settings = new SettingsManager(this);
        if (settings.isGlobalAccessEnabled()) {
            SqlApiHelper.getTunnelManager().startTunnel();
        }

        // Register tunnel status listener to refresh notification dynamically
        if (tunnelListener == null) {
            tunnelListener = (state, message, publicUrl) -> updateNotification();
            SqlApiHelper.getTunnelManager().addListener(tunnelListener);
        }

        // Build and display initial notification
        updateNotification();

        return START_STICKY;
    }

    private void updateNotification() {
        try {
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            Intent stopIntent = new Intent(this, SqlApiService.class);
            stopIntent.setAction(ACTION_STOP_SERVER);
            PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, pendingFlags);

            Intent mainIntent = new Intent(this, MainActivity.class);
            PendingIntent mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, pendingFlags);

            String hostAddress = SqlApiHelper.getNetworkHostAddress();
            int activePort = SqlApiHelper.getApiServer().getActivePort();
            String bindError = SqlApiHelper.getApiServer().getBindErrorMessage();
            String protocol = SqlApiHelper.getApiServer().isTlsEnabled() ? "https://" : "http://";
            String localEndpoint = protocol + hostAddress + ":" + activePort + "/api/query";

            SqlTunnelManager tunnelMgr = SqlApiHelper.getTunnelManager();
            boolean isGlobalOnline = tunnelMgr != null && tunnelMgr.isTunnelActive();
            String globalUrl = tunnelMgr != null ? tunnelMgr.getPublicUrl() : null;

            String title;
            String contentText;
            String bigText;

            if (bindError != null) {
                title = "PocketSQL API Server (Error)";
                contentText = "API Server bind failed: " + bindError;
                bigText = contentText;
            } else if (isGlobalOnline && globalUrl != null) {
                title = "PocketSQL API Server (Global Online 🌐)";
                contentText = "🌐 " + globalUrl + "/api/query";
                bigText = "🌐 Global: " + globalUrl + "/api/query\n📍 LAN: " + localEndpoint;
            } else if (tunnelMgr != null && tunnelMgr.getState() == SqlTunnelManager.TunnelState.CONNECTING) {
                title = "PocketSQL API Server (Connecting Global...)";
                contentText = "Connecting Global Tunnel... | " + localEndpoint;
                bigText = "Connecting to global gateway...\n📍 LAN: " + localEndpoint;
            } else {
                title = "PocketSQL API Server (LAN Only)";
                contentText = "Running on " + localEndpoint;
                bigText = "📍 Local Network: " + localEndpoint;
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(mainPendingIntent)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, notification);
                }
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tunnelListener != null) {
            try {
                SqlApiHelper.getTunnelManager().removeListener(tunnelListener);
            } catch (Exception ignored) {}
            tunnelListener = null;
        }

        try {
            SqlApiHelper.getTunnelManager().stopTunnel();
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }

        try {
            SqlApiHelper.getApiServer().stop();
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }

        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "PocketSQL API Server Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps the SQL API Server and Global Tunnel running in the background.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
