package com.mysql.pocketsql.engine;

import com.mysql.pocketsql.BuildConfig;

public class SqlLog {
    public static void e(String tag, String msg, Throwable t) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e(tag, msg, t);
        }
    }

    public static void printStackTrace(Throwable t) {
        if (BuildConfig.DEBUG && t != null) {
            t.printStackTrace();
        }
    }

    public static void err(String msg) {
        if (BuildConfig.DEBUG) {
            System.err.println(msg);
        }
    }
}
