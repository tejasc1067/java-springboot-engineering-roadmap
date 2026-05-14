import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CachedThreadPoolExample {

    public static void main(String[] args) {

        ExecutorService executorService =
                Executors.newCachedThreadPool();

        for (int index = 1;
             index <= 5;
             index++) {

            int taskId = index;

            executorService.submit(() -> {

                System.out.println(
                        "Executing Task: "
                                + taskId
                );
            });
        }

        executorService.shutdown();
    }
}