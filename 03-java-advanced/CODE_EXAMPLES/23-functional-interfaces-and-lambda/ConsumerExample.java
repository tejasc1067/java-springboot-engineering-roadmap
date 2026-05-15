import java.util.function.Consumer;

public class ConsumerExample {

    public static void main(String[] args) {

        Consumer<String> logger =
                message -> System.out.println(
                        "Log: " + message
                );

        logger.accept(
                "Application Started"
        );
    }
}