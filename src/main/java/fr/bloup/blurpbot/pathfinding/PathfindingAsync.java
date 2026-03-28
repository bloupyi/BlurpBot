package fr.bloup.blurpbot.pathfinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool dédié pour l’A* sur snapshot (évite de saturer le common pool).
 */
public final class PathfindingAsync {
    private static final AtomicInteger THREAD = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors() / 2)),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "BlurpBot-path-" + THREAD.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private PathfindingAsync() {
    }

    public static ExecutorService executor() {
        return POOL;
    }
}
