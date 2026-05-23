import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureGetAndCancel {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // 1) blocking get
        Future<String> f1 = pool.submit(() -> { Thread.sleep(150); return "ready"; });
        System.out.println("blocking get: " + f1.get());

        // 2) timed get
        Future<String> f2 = pool.submit(() -> { Thread.sleep(500); return "too slow"; });
        try {
            f2.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("timed get: timed out as expected");
        }
        f2.cancel(true);

        // 3) explicit cancel
        Future<String> f3 = pool.submit(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted");
            }
            return "done";
        });
        Thread.sleep(100);
        boolean cancelled = f3.cancel(true);
        System.out.println("cancel returned: " + cancelled + ", isCancelled=" + f3.isCancelled());

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }
}
