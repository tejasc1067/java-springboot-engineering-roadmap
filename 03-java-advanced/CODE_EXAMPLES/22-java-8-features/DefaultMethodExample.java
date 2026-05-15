interface PaymentService {

    default void paymentStatus() {

        System.out.println(
                "Payment Completed"
        );
    }
}

class CreditCardPayment
        implements PaymentService {
}

public class DefaultMethodExample {

    public static void main(String[] args) {

        CreditCardPayment payment =
                new CreditCardPayment();

        payment.paymentStatus();
    }
}