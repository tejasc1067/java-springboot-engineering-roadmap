abstract class PaymentService {

    abstract void pay();
}

class UpiPayment extends PaymentService {

    @Override
    void pay() {

        System.out.println(
                "UPI Payment Completed"
        );
    }
}

class CardPayment extends PaymentService {

    @Override
    void pay() {

        System.out.println(
                "Card Payment Completed"
        );
    }
}

public class PaymentAbstractionExample {

    public static void main(String[] args) {

        PaymentService payment;

        payment = new UpiPayment();

        payment.pay();

        payment = new CardPayment();

        payment.pay();
    }
}