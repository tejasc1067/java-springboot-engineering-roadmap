class PaymentService {

    void pay() {

        System.out.println(
                "Generic Payment Service"
        );
    }
}

class UpiPaymentService extends PaymentService {

    @Override
    void pay() {

        System.out.println(
                "UPI Payment Completed"
        );
    }
}

class CardPaymentService extends PaymentService {

    @Override
    void pay() {

        System.out.println(
                "Card Payment Completed"
        );
    }
}

public class BackendServicePolymorphismExample {

    public static void main(String[] args) {

        PaymentService paymentService;

        paymentService = new UpiPaymentService();

        paymentService.pay();

        paymentService = new CardPaymentService();

        paymentService.pay();
    }
}