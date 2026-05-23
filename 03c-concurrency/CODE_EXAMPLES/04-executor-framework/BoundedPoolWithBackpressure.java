import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedPoolWithBackpressure {

    public static void main(String[] args) throws InterruptedException {
        // 2 core, 2 max, queue of 3. Total in-flight capacity = 2 + 3 = 5.
        // Beyond that, CallerRunsPolicy makes the submitter run the task itself.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            2, 2,
            0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(3),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        for (int i = 0; i < 12; i++) {
            final int id = i;
            pool.execute(() -> {
                String worker = Thread.currentThread().getName();
                System.out.println("task " + id + " on " + worker);
                try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println();
        System.out.println("Notice some tasks ran on 'main' -- CallerRunsPolicy");
        System.out.println("pushes work back onto the submitter when the pool + queue are full.");
    }
}
