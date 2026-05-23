import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FixedPoolBasics {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // Submit 10 tasks to a pool of 4. The same 4 threads handle all of them.
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            pool.execute(() -> {
                String worker = Thread.currentThread().getName();
                System.out.println("task " + taskId + " on " + worker);
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("pool drained.");
    }
}
