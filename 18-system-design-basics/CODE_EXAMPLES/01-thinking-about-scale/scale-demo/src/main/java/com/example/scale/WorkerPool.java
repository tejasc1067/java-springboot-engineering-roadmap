package com.example.scale;

import java.util.PriorityQueue;
import java.util.Random;

/**
 * A discrete-event model of a server with a fixed pool of worker threads.
 * Time is simulated in milliseconds, so a "60-second" run takes a few milliseconds of real time.
 *
 * Requests arrive at random (a Poisson process, the usual model for many independent users),
 * wait for a free worker, and hold it for {@code serviceMillis}.
 */
public final class WorkerPool {

    public record Result(double throughputPerSecond, double averageWaitMillis, double maxWaitMillis) { }

    private WorkerPool() { }

    public static Result run(int workers, double serviceMillis, double arrivalsPerSecond,
                             int durationSeconds, long seed) {
        Random random = new Random(seed);
        PriorityQueue<Double> workerFreeAt = new PriorityQueue<>();
        for (int i = 0; i < workers; i++) workerFreeAt.add(0.0);

        double durationMillis = durationSeconds * 1000.0;
        double meanGapMillis = 1000.0 / arrivalsPerSecond;
        double now = 0;
        long arrived = 0, finishedInTime = 0;
        double totalWait = 0, maxWait = 0;

        while (true) {
            now += -Math.log(1 - random.nextDouble()) * meanGapMillis;   // exponential gap between arrivals
            if (now > durationMillis) break;
            arrived++;

            double start = Math.max(now, workerFreeAt.poll());   // earliest free worker, or wait for it
            double finish = start + serviceMillis;
            workerFreeAt.add(finish);

            double wait = start - now;
            totalWait += wait;
            maxWait = Math.max(maxWait, wait);
            if (finish <= durationMillis) finishedInTime++;
        }
        return new Result(finishedInTime / (double) durationSeconds, totalWait / arrived, maxWait);
    }
}
