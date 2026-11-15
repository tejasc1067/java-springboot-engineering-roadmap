import java.util.ArrayList;
import java.util.List;

// The adapter is the ONE class that knows the vendor's shape (dollars, int status codes).
// Everyone else talks to PaymentGateway, the interface our code actually wants.
//
// COMPARE WITH: DirectVendorCallsBroken.java

class AcmePayClient {
    final List<Double> charged = new ArrayList<>();

    int makePayment(String account, double amountInDollars) {
        if (amountInDollars <= 0) return 17; // vendor's "invalid amount" code
        charged.add(amountInDollars);
        return 0;
    }
}

// The interface our code wants: cents in, exception on failure.
interface PaymentGateway {
    void charge(String customerId, long amountInCents);
}

class PaymentFailedException extends RuntimeException {
    PaymentFailedException(String message) { super(message); }
}

class AcmePaymentAdapter implements PaymentGateway {
    private final AcmePayClient acme;
    AcmePaymentAdapter(AcmePayClient acme) { this.acme = acme; }

    @Override
    public void charge(String customerId, long amountInCents) {
        int status = acme.makePayment(customerId, amountInCents / 100.0);
        if (status != 0) {
            throw new PaymentFailedException("Acme rejected payment, code " + status);
        }
    }
}

class CheckoutService {
    private final PaymentGateway payments;
    CheckoutService(PaymentGateway payments) { this.payments = payments; }
    void pay(String customerId, long amountInCents) { payments.charge(customerId, amountInCents); }
}

class SubscriptionRenewal {
    private final PaymentGateway payments;
    SubscriptionRenewal(PaymentGateway payments) { this.payments = payments; }
    void renew(String customerId, long amountInCents) { payments.charge(customerId, amountInCents); }
}

public class PaymentAdapter {
    public static void main(String[] args) {
        AcmePayClient acme = new AcmePayClient();
        PaymentGateway gateway = new AcmePaymentAdapter(acme);

        new CheckoutService(gateway).pay("cust-42", 1999);
        new SubscriptionRenewal(gateway).renew("cust-42", 1999);

        System.out.printf("Checkout charged $%.2f%n", acme.charged.get(0));
        System.out.printf("Renewal  charged $%.2f%n", acme.charged.get(1));
        if (!acme.charged.equals(List.of(19.99, 19.99))) throw new AssertionError(acme.charged);

        try {
            gateway.charge("cust-42", 0);
            throw new AssertionError("expected PaymentFailedException");
        } catch (PaymentFailedException e) {
            System.out.println("Error codes become exceptions too: " + e.getMessage());
        }
    }
}
