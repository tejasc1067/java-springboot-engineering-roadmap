import java.util.concurrent.CompletableFuture;

public class BackendAsyncApiExample {

    public static void main(String[] args) {

        CompletableFuture<String> userService =
                CompletableFuture.supplyAsync(() -> {

                    return "User Details";
                });

        CompletableFuture<String> orderService =
                CompletableFuture.supplyAsync(() -> {

                    return "Order Details";
                });

        CompletableFuture<Void> combinedFuture =
                CompletableFuture.allOf(
                        userService,
                        orderService
                );

        combinedFuture.join();

        System.out.println(
                userService.join()
        );

        System.out.println(
                orderService.join()
        );
    }
}