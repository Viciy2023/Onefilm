package com.onefilm.android.tv.server;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

/**
 * Bounded replacement for {@link NanoHTTPD.DefaultAsyncRunner}.
 *
 * <p>The default runner creates one thread per connection. On TV boxes the
 * local proxy can be hit by many short-lived requests at once (webview, 115
 * netdisk, image cache). Creating a thread then throws
 * {@code OutOfMemoryError: pthread_create failed}, which is uncaught and kills
 * the whole process (SIGABRT).
 *
 * <p>This runner keeps the thread count bounded, queues the overflow and never
 * lets an error escape: a dropped request only fails that one connection.
 */
final class BoundedAsyncRunner implements NanoHTTPD.AsyncRunner {

    private static final int CORE_THREADS = 4;
    private static final int MAX_THREADS = 16;
    private static final int QUEUE_CAPACITY = 64;

    private final ThreadPoolExecutor pool;

    BoundedAsyncRunner() {
        pool = new ThreadPoolExecutor(
                CORE_THREADS,
                MAX_THREADS,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(QUEUE_CAPACITY),
                new ThreadFactory() {
                    private final AtomicInteger sequence = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, "onefilm-http-" + sequence.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                // Bounded queue is full: run on the acceptor thread as backpressure
                // instead of spawning more threads.
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void closeAll() {
        pool.shutdownNow();
    }

    @Override
    public void closed(NanoHTTPD.ClientHandler clientHandler) {
        // Nothing to track: the pool owns the threads.
    }

    @Override
    public void exec(NanoHTTPD.ClientHandler clientHandler) {
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        clientHandler.run();
                    } catch (Throwable ignored) {
                        // One broken connection must never abort the process.
                    } finally {
                        closed(clientHandler);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Rejected execution or thread creation failure: drop this connection.
        }
    }
}
