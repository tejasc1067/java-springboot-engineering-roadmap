import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolAwarenessExample {

    public static void main(String[] args) {

        ExecutorService executorService =
                Executors.newFixedThreadPool(3);

        for (int i = 1; i <= 5; i++) {

            int taskId = i;

            executorService.submit(() -> {

                System.out.println(
                        "Executing Task: "
                                + taskId
                );

                try {

                    Thread.sleep(2000);

                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
            });
        }

        executorService.shutdown();
    }
}