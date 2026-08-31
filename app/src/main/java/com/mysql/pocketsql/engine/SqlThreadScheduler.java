package com.mysql.pocketsql.engine;

import android.os.Process;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * SqlThreadScheduler — Manages CPU scheduling and thread priority allocation
 * for database initialization, query processing, background tasks, and REST API workers.
 */
public class SqlThreadScheduler {

    /**
     * Runs a critical database task (such as database engine init or DB seeding)
     * using CPU scheduling priority THREAD_PRIORITY_MORE_FAVORABLE (-3) to ensure
     * rapid completion without blocking UI.
     */
    public static void runDatabaseInitTask(Runnable runnable) {
        new Thread(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
            } catch (Throwable ignored) {}
            runnable.run();
        }, "PocketSQL-DbInit").start();
    }

    /**
     * Runs a background task (such as background file encryption scanning)
     * using CPU scheduling priority THREAD_PRIORITY_BACKGROUND (+10) so it does not compete
     * with high-priority database operations.
     */
    public static void runBackgroundTask(Runnable runnable) {
        new Thread(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            } catch (Throwable ignored) {}
            runnable.run();
        }, "PocketSQL-BackgroundIO").start();
    }

    /**
     * Creates an ExecutorService for REST API requests with threads prioritized via CPU scheduling.
     */
    public static ExecutorService createApiServerThreadPool() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(() -> {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    } catch (Throwable ignored) {}
                    r.run();
                }, "PocketSQL-ApiWorker-" + (++count));
                t.setDaemon(true);
                return t;
            }
        });
    }
}
