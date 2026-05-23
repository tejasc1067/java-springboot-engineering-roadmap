import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterCAS {

    public static void main(String[] args) throws InterruptedException {
        // High-level convenience.
        AtomicInteger counter = new AtomicInteger();
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < 100_000; j++) counter.incrementAndGet(); });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        System.out.println("incrementAndGet result: " + counter.get());

        // Manual CAS loop -- this is what incrementAndGet effectively does internally.
        AtomicInteger manual = new AtomicInteger();
        Thread[] ms = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ms[i] = new Thread(() -> {
                for (int j = 0; j < 100_000; j++) {
                    int current, next;
                    do {
                        current = manual.get();
                        next = current + 1;
                    } while (!manual.compareAndSet(current, next));
                }
            });
        }
        for (Thread t : ms) t.start();
        for (Thread t : ms) t.join();
        System.out.println("manual CAS loop result: " + manual.get());

        System.out.println();
        System.out.println("CAS retries on conflict. Under heavy write contention, retries dominate -- see LongAdderVsAtomicLong.java.");
    }
}
