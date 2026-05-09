interface MessagePrinter {

    void printMessage(String message);
}

public class LambdaExpressionExample {

    public static void main(String[] args) {

        // Lambda Expression
        MessagePrinter printer =
                (message) -> System.out.println(message);

        printer.printMessage("Learning Java 8 Lambda Expressions");
    }
}