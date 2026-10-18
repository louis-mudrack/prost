package dev.louismudrack.prost.storage;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** A worker the test drains by hand; runs tasks inline until {@link #hold()} is called. */
final class ManualWorker extends AbstractExecutorService {
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private boolean held;
    private boolean shutdown;

    void hold() { held = true; }

    void drain() {
        held = false;
        for (Runnable r; (r = queue.poll()) != null; ) r.run();
    }

    @Override public void execute(Runnable task) {
        if (held) queue.add(task);
        else task.run();
    }

    @Override public void shutdown() { shutdown = true; }
    @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
    @Override public boolean isShutdown() { return shutdown; }
    @Override public boolean isTerminated() { return shutdown; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
}
