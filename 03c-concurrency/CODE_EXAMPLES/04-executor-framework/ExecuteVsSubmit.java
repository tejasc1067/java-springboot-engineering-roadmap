import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecuteVsSubmit {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // execute: fire and forget, no return value.
        pool.execute(() -> System.out.println("execute(): no return"));

        // submit(Runnable): returns Future<?>, get() returns null on completion.
        Future<?> f1 = pool.submit(() -> System.out.println("submit(Runnable): returns Future"));
        System.out.println("Runnable.get() returned: " + f1.get());

        // submit(Callable): returns Future<T>, get() returns the value.
        Future<Integer> f2 = pool.submit(() -> 7 * 6);
        System.out.println("Callable.get() returned: " + f2.get());

        // Exceptions inside submit() are captured in the Future, surfaced on get().
        Future<Integer> f3 = pool.submit(() -> { throw new RuntimeException("boom"); });
        try { f3.get(); }
        catch (ExecutionException e) {
            System.out.println("submit() exception surfaced via get(): " + e.getCause().getMessage());
        }

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
