import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerCounter {

    static AtomicInteger counter = new AtomicInteger();    // CAS-based, lock-free

    public static void main(String[] args) throws InterruptedException {
        int threads = 8, perThread = 200_000;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) counter.incrementAndGet();
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        int expected = threads * perThread;
        System.out.println("expected: " + expected);
        System.out.println("actual:   " + counter.get());
        System.out.println("match:    " + (expected == counter.get()));
    }
}
