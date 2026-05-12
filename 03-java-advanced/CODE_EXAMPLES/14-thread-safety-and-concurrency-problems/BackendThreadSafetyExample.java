class PaymentProcessor {

    private int processedPayments = 0;

    synchronized void processPayment() {

        processedPayments++;

        System.out.println(
                "Processed Payments: "
                        + processedPayments
        );
    }
}

public class BackendThreadSafetyExample {

    public static void main(String[] args) {

        PaymentProcessor processor =
                new PaymentProcessor();

        Runnable task =
                processor::processPayment;

        new Thread(task).start();

        new Thread(task).start();
    }
}