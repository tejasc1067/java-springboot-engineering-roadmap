class Payment {

    void pay() {

        System.out.println(
                "Generic Payment"
        );
    }
}

class CreditCardPayment extends Payment {

    @Override
    void pay() {

        System.out.println(
                "Credit Card Payment Processed"
        );
    }
}

public class RuntimePolymorphismExample {

    public static void main(String[] args) {

        Payment payment =
                new CreditCardPayment();

        payment.pay();
    }
}