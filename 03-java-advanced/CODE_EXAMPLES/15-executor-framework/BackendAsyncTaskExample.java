import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackendAsyncTaskExample {

    public static void main(String[] args) {

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        executorService.submit(() -> {

            System.out.println(
                    "Sending Email Notification"
            );
        });

        executorService.submit(() -> {

            System.out.println(
                    "Generating Invoice"
            );
        });

        executorService.shutdown();
    }
}