import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class LongAdderVsAtomicLong {

    public static void main(String[] args) throws InterruptedException {
        int threads = 16, perThread = 500_000;

        AtomicLong al = new AtomicLong();
        long t1 = run(threads, () -> { for (int j = 0; j < perThread; j++) al.incrementAndGet(); });
        System.out.println("AtomicLong: " + t1 + " ms, value=" + al.get());

        LongAdder la = new LongAdder();
        long t2 = run(threads, () -> { for (int j = 0; j < perThread; j++) la.increment(); });
        System.out.println("LongAdder:  " + t2 + " ms, value=" + la.sum());

        System.out.println();
        System.out.println("LongAdder spreads writes across cells; AtomicLong all contend on one slot.");
    }

    static long run(int threads, Runnable r) throws InterruptedException {
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) ts[i] = new Thread(r);
        long start = System.nanoTime();
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
