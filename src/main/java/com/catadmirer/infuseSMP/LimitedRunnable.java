package com.catadmirer.infuseSMP;

import java.util.function.IntConsumer;

/**
 * Legacy bounded-loop helper retained for binary/source compatibility.
 *
 * <p>It deliberately no longer extends BukkitRunnable: callers should schedule it with the
 * entity/region/global scheduler that owns the state they touch.</p>
 */
public final class LimitedRunnable implements Runnable {
    private final IntConsumer task;
    private final int iterations;
    private int iteration;
    private boolean cancelled;

    public LimitedRunnable(int iterations, Runnable task) {
        this(iterations, ignored -> task.run());
    }

    public LimitedRunnable(int iterations, IntConsumer task) {
        if (iterations < 0) throw new IllegalArgumentException("iterations must be >= 0");
        this.iterations = iterations;
        this.task = task;
    }

    @Override
    public void run() {
        if (cancelled || iteration >= iterations) {
            cancelled = true;
            return;
        }
        task.accept(iteration++);
        if (iteration >= iterations) cancelled = true;
    }

    public void cancel() { cancelled = true; }
    public boolean isCancelled() { return cancelled; }
}
