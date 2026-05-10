interface PaymentService {

    void pay();
}

class UpiPaymentService
        implements PaymentService {

    @Override
    public void pay() {

        System.out.println(
                "UPI Payment Completed"
        );
    }
}

class CardPaymentService
        implements PaymentService {

    @Override
    public void pay() {

        System.out.println(
                "Card Payment Completed"
        );
    }
}

public class OpenClosedPrincipleExample {

    public static void main(String[] args) {

        PaymentService paymentService;

        paymentService =
                new UpiPaymentService();

        paymentService.pay();

        paymentService =
                new CardPaymentService();

        paymentService.pay();
    }
}