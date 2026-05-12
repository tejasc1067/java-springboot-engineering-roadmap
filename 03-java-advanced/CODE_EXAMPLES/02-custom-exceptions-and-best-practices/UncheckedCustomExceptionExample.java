class InvalidOrderStateException
        extends RuntimeException {

    InvalidOrderStateException(
            String message
    ) {

        super(message);
    }
}

public class UncheckedCustomExceptionExample {

    static void processOrder(
            boolean paymentCompleted
    ) {

        if (!paymentCompleted) {

            throw new InvalidOrderStateException(
                    "Payment Not Completed"
            );
        }

        System.out.println(
                "Order Processed"
        );
    }

    public static void main(String[] args) {

        processOrder(false);
    }
}