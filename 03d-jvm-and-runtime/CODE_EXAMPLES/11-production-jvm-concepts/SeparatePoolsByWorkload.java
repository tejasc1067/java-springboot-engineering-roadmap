import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SeparatePoolsByWorkload {

    public static void main(String[] args) throws InterruptedException {
        // Two separate pools: latency-sensitive requests and heavy batch jobs.
        ExecutorService requestPool = Executors.newFixedThreadPool(4);
        ExecutorService batchPool   = Executors.newFixedThreadPool(2);

        AtomicInteger reqDone   = new AtomicInteger();
        AtomicInteger batchDone = new AtomicInteger();

        // Submit a stream of slow batch jobs first.
        for (int i = 0; i < 5; i++) {
            final int id = i;
            batchPool.submit(() -> {
                try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                System.out.println("batch " + id + " done");
                batchDone.incrementAndGet();
            });
        }

        // Now submit a burst of small requests. They land on a DIFFERENT pool, so they're not
        // queued behind the long-running batch jobs.
        long start = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            final int id = i;
            requestPool.submit(() -> {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                reqDone.incrementAndGet();
            });
        }

        requestPool.shutdown();
        requestPool.awaitTermination(30, TimeUnit.SECONDS);
        long requestsFinishedMs = System.currentTimeMillis() - start;

        batchPool.shutdown();
        batchPool.awaitTermination(30, TimeUnit.SECONDS);
        long totalMs = System.currentTimeMillis() - start;

        System.out.println();
        System.out.println("requests done:      " + reqDone.get() + " in " + requestsFinishedMs + " ms");
        System.out.println("batches done:       " + batchDone.get() + " in " + totalMs + " ms");
        System.out.println();
        System.out.println("Requests finished long before batches, even though batches were submitted first.");
        System.out.println("Lesson: separate pools by workload class so one workload can't starve another.");
    }
}
