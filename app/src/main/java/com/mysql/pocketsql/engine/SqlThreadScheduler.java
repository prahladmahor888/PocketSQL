package com.mysql.pocketsql.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SqlThreadScheduler — Advanced CPU thread scheduler and priority allocator
 * for asynchronous database initialization, high-priority interactive query execution,
 * background IO tasks, and REST API worker threads.
 */
public class SqlThreadScheduler {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    // Single background thread for sequential database seeding (avoids concurrency lock contention)
    private static final ExecutorService DB_INIT_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(() -> {
                try {
                    int priority = SqlEnvConfig.getInt("INIT_THREAD_PRIORITY", Process.THREAD_PRIORITY_BACKGROUND);
                    Process.setThreadPriority(priority);
                } catch (Throwable ignored) {}
                r.run();
            }, "PocketSQL-DbInitWorker");
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        }
    });

    // High-priority thread pool for user interactive SQL queries (ensures instantaneous execution)
    private static final AtomicInteger QUERY_THREAD_COUNTER = new AtomicInteger(0);
    private static final ExecutorService QUERY_EXECUTOR = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(() -> {
                try {
                    int priority = SqlEnvConfig.getInt("QUERY_THREAD_PRIORITY", Process.THREAD_PRIORITY_MORE_FAVORABLE);
                    Process.setThreadPriority(priority);
                } catch (Throwable ignored) {}
                r.run();
            }, "PocketSQL-QueryWorker-" + QUERY_THREAD_COUNTER.incrementAndGet());
            t.setPriority(Thread.MAX_PRIORITY - 1);
            return t;
        }
    });

    // Background thread pool for async suggestions, formatting, and file IO
    private static final AtomicInteger ASYNC_THREAD_COUNTER = new AtomicInteger(0);
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(() -> {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } catch (Throwable ignored) {}
                r.run();
            }, "PocketSQL-AsyncWorker-" + ASYNC_THREAD_COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * Runs a database initialization task in the dedicated background executor with lower CPU priority,
     * allowing the UI and user queries to execute without any stutter or freezing.
     */
    public static void runDatabaseInitTask(Runnable runnable) {
        if (runnable == null) return;
        DB_INIT_EXECUTOR.execute(runnable);
    }

    /**
     * Runs an interactive user query in the high-priority query executor.
     */
    public static void runQueryTask(Runnable runnable) {
        if (runnable == null) return;
        QUERY_EXECUTOR.execute(runnable);
    }

    /**
     * Runs a general background IO or calculation task.
     */
    public static void runBackgroundTask(Runnable runnable) {
        if (runnable == null) return;
        ASYNC_EXECUTOR.execute(runnable);
    }

    /**
     * Posts a runnable to run on the Main/UI thread.
     */
    public static void runOnMainThread(Runnable runnable) {
        if (runnable == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }

    /**
     * Posts a runnable to run on the Main/UI thread after a delay.
     */
    public static void postDelayed(Runnable runnable, long delayMillis) {
        if (runnable == null) return;
        MAIN_HANDLER.postDelayed(runnable, delayMillis);
    }

    /**
     * Removes callbacks from the main handler.
     */
    public static void removeCallbacks(Runnable runnable) {
        if (runnable == null) return;
        MAIN_HANDLER.removeCallbacks(runnable);
    }

    /**
     * Creates an ExecutorService for REST API requests with threads prioritized via CPU scheduling.
     */
    public static ExecutorService createApiServerThreadPool() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(() -> {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    } catch (Throwable ignored) {}
                    r.run();
                }, "PocketSQL-ApiWorker-" + count.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }
}
