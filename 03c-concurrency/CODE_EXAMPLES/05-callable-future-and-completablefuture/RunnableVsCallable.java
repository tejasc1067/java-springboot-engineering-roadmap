import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RunnableVsCallable {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Runnable: no return value, no checked exceptions.
        Runnable r = () -> System.out.println("Runnable: side effect only");
        Future<?> rf = pool.submit(r);
        System.out.println("Runnable Future.get() = " + rf.get());   // always null

        // Callable: returns a value AND may throw a checked exception.
        Callable<Integer> c = () -> {
            if (Math.random() < 0) throw new IOException("never thrown, but the signature allows it");
            return 42;
        };
        Future<Integer> cf = pool.submit(c);
        System.out.println("Callable Future.get() = " + cf.get());

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }
}
