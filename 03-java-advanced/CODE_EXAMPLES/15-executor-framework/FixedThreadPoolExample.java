import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FixedThreadPoolExample {

    public static void main(String[] args) {

        ExecutorService executorService =
                Executors.newFixedThreadPool(3);

        for (int index = 1;
             index <= 5;
             index++) {

            int taskId = index;

            executorService.submit(() -> {

                System.out.println(
                        "Processing Task: "
                                + taskId
                                + " by "
                                + Thread.currentThread().getName()
                );
            });
        }

        executorService.shutdown();
    }
}