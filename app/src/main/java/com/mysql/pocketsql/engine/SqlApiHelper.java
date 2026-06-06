package com.mysql.pocketsql.engine;

import android.content.Context;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SqlApiHelper {
    private static DatabaseEngine engine;
    private static SqlApiKeyManager apiKeyManager;
    private static SqlApiServer apiServer;

    public static synchronized void init(Context context) {
        if (engine == null) {
            Context appCtx = context.getApplicationContext();
            File extFiles = appCtx.getExternalFilesDir(null);
            if (extFiles == null) {
                extFiles = appCtx.getFilesDir();
            }
            File pocketsqlDir = new File(extFiles, "PocketSQL");
            if (!pocketsqlDir.exists()) {
                pocketsqlDir.mkdirs();
            }
            engine = new DatabaseEngine(pocketsqlDir);
            apiKeyManager = new SqlApiKeyManager(pocketsqlDir);
            apiKeyManager.initializeDefaultKey();
            apiServer = new SqlApiServer(engine, apiKeyManager);
        }
    }

    public static DatabaseEngine getEngine() {
        return engine;
    }

    public static SqlApiKeyManager getApiKeyManager() {
        return apiKeyManager;
    }

    public static SqlApiServer getApiServer() {
        return apiServer;
    }

    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "localhost";
    }
}
