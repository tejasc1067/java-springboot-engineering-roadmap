class Payment {

    void processPayment() {

        System.out.println(
                "Generic Payment Processing"
        );
    }
}

class CreditCardPayment extends Payment {

    @Override
    void processPayment() {

        System.out.println(
                "Credit Card Payment Processed"
        );
    }
}

public class ParentReferenceExample {

    public static void main(String[] args) {

        Payment payment =
                new CreditCardPayment();

        payment.processPayment();
    }
}