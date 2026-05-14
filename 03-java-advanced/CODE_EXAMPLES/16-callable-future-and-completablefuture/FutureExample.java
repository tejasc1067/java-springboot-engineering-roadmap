import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureExample {

    public static void main(String[] args)
            throws Exception {

        ExecutorService executorService =
                Executors.newSingleThreadExecutor();

        Future<String> future =
                executorService.submit(() -> {

                    Thread.sleep(2000);

                    return "Task Completed";
                });

        System.out.println(
                future.get()
        );

        executorService.shutdown();
    }
}