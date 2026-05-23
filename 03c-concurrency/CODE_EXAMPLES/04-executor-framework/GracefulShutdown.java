import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GracefulShutdown {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 4; i++) {
            final int id = i;
            pool.execute(() -> {
                System.out.println("task " + id + " starting");
                try {
                    Thread.sleep(id == 3 ? 5000 : 200);   // task 3 is intentionally slow
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("task " + id + " interrupted (responding to shutdownNow)");
                    return;
                }
                System.out.println("task " + id + " finished");
            });
        }

        pool.shutdown();                            // refuse new tasks; let queued ones run
        System.out.println("shutdown() called -- waiting up to 1s for graceful drain");

        if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
            System.out.println("not drained in time -- calling shutdownNow() to interrupt stragglers");
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
        System.out.println("pool isTerminated? " + pool.isTerminated());
    }
}
