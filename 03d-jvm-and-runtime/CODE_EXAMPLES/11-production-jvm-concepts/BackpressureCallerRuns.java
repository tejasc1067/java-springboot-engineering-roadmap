import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BackpressureCallerRuns {

    public static void main(String[] args) throws InterruptedException {
        // 2 workers, queue capacity 3 -> capacity in flight = 5.
        // CallerRunsPolicy makes the submitting thread run the task itself when full.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            2, 2,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(3),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        AtomicInteger ranOnMain = new AtomicInteger();
        AtomicInteger ranOnWorker = new AtomicInteger();

        long start = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            final int id = i;
            pool.execute(() -> {
                if (Thread.currentThread().getName().equals("main")) ranOnMain.incrementAndGet();
                else ranOnWorker.incrementAndGet();
                try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("ran on workers: " + ranOnWorker.get());
        System.out.println("ran on main:    " + ranOnMain.get());
        System.out.println("elapsed:        " + elapsed + " ms");
        System.out.println();
        System.out.println("CallerRunsPolicy is backpressure: when the pool can't keep up, the producer");
        System.out.println("naturally slows down because it's now doing the work too.");
    }
}
