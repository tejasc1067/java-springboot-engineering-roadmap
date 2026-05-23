public class SynchronizedMethodVsBlock {

    static int counter = 0;
    private static final Object lock = new Object();

    // Whole method is locked, including the simulated "expensive" work
    // (here just a bit of math) that doesn't touch shared state.
    static synchronized void incrementMethodLocked() {
        for (int i = 0; i < 50; i++) Math.sqrt(i);     // not shared, but still under the lock
        counter++;
    }

    // Only the actual shared write is locked. Everything else runs concurrently.
    static void incrementBlockLocked() {
        for (int i = 0; i < 50; i++) Math.sqrt(i);     // outside the lock
        synchronized (lock) {
            counter++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("method-locked: " + timeRun(SynchronizedMethodVsBlock::incrementMethodLocked) + " ms");
        counter = 0;
        System.out.println("block-locked:  " + timeRun(SynchronizedMethodVsBlock::incrementBlockLocked) + " ms");
        System.out.println();
        System.out.println("Both are correct. The block version typically wins on throughput");
        System.out.println("because threads only contend for the tiny critical section.");
    }

    static long timeRun(Runnable op) throws InterruptedException {
        int threads = 8, perThread = 50_000;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < perThread; j++) op.run(); });
        }
        long start = System.nanoTime();
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
