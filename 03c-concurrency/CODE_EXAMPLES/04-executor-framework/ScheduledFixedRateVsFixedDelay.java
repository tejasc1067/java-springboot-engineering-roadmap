import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledFixedRateVsFixedDelay {

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        long start = System.currentTimeMillis();
        AtomicInteger rateCount = new AtomicInteger();
        AtomicInteger delayCount = new AtomicInteger();

        // Task takes 200ms. Period is 100ms.
        // fixedRate: tries to start every 100ms, so back-to-back (it falls behind).
        scheduler.scheduleAtFixedRate(() -> {
            long t = System.currentTimeMillis() - start;
            System.out.println("[fixedRate]  start  t=" + t + "ms  count=" + rateCount.incrementAndGet());
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // fixedDelay: waits 100ms AFTER the task finishes -> spacing ~300ms.
        scheduler.scheduleWithFixedDelay(() -> {
            long t = System.currentTimeMillis() - start;
            System.out.println("[fixedDelay] start  t=" + t + "ms  count=" + delayCount.incrementAndGet());
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, 0, 100, TimeUnit.MILLISECONDS);

        Thread.sleep(1500);
        scheduler.shutdownNow();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("fixedRate started "  + rateCount.get()  + " runs (period 100ms, task 200ms)");
        System.out.println("fixedDelay started " + delayCount.get() + " runs (delay 100ms after end)");
    }
}
