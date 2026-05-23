import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreRateLimit {

    static final Semaphore tickets = new Semaphore(3);   // at most 3 in flight at once
    static final AtomicInteger inFlight = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        Thread[] ts = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int id = i;
            ts[i] = new Thread(() -> callDownstream(id));
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
    }

    static void callDownstream(int id) {
        try {
            tickets.acquire();
            int current = inFlight.incrementAndGet();
            System.out.println("call " + id + " started, in-flight=" + current);
            try { Thread.sleep(120); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            inFlight.decrementAndGet();
            System.out.println("call " + id + " done");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            tickets.release();
        }
    }
}
