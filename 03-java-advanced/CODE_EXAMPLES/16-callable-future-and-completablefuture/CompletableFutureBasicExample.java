import java.util.concurrent.CompletableFuture;

public class CompletableFutureBasicExample {

    public static void main(String[] args) {

        CompletableFuture<Void> future =
                CompletableFuture.runAsync(() -> {

                    System.out.println(
                            "Async Task Executed"
                    );
                });

        future.join();
    }
}