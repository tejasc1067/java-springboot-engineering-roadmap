import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteLockBenefit {

    // Simulate a "read" that holds the lock for ~1ms of real work.
    // The whole point of RWLock is parallelism over the duration the lock is held;
    // with a 10ns read, lock overhead dominates and the comparison is unfair.
    static final int WORK_BUSY_MICROS = 1_000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("plain ReentrantLock (mutual exclusion across all readers):");
        long t1 = runWithPlainLock();
        System.out.println("  8 readers x 50 reads, each 1ms work:  " + t1 + " ms");

        System.out.println("ReentrantReadWriteLock (readers run in parallel):");
        long t2 = runWithReadWriteLock();
        System.out.println("  same workload:                        " + t2 + " ms");

        System.out.println();
        System.out.println("With non-trivial read work, RWLock lets all 8 readers run at once.");
        System.out.println("Plain Lock forces them to take turns -- ~8x slower.");
    }

    static long runWithPlainLock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        return runReaders(8, 50, () -> {
            lock.lock();
            try { busyWait(WORK_BUSY_MICROS); }
            finally { lock.unlock(); }
        });
    }

    static long runWithReadWriteLock() throws InterruptedException {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        return runReaders(8, 50, () -> {
            rw.readLock().lock();
            try { busyWait(WORK_BUSY_MICROS); }
            finally { rw.readLock().unlock(); }
        });
    }

    static long runReaders(int threads, int perThread, Runnable readOp) throws InterruptedException {
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < perThread; j++) readOp.run(); });
        }
        long start = System.nanoTime();
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        return (System.nanoTime() - start) / 1_000_000;
    }

    static void busyWait(int micros) {
        long endNs = System.nanoTime() + micros * 1_000L;
        while (System.nanoTime() < endNs) { /* spin */ }
    }
}
