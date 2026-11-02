package de.jakob.lotm.util.helper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs long structure / biome searches off the server thread. They get their own small pool instead of the
 * common ForkJoin pool, so a slow search can't starve unrelated async work.
 */
public final class AsyncSearch {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "LOTMCraft-Search-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private AsyncSearch() {}
}
