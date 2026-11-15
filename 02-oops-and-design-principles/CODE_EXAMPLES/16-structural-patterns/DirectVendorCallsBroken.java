import java.util.ArrayList;
import java.util.List;

// Our code thinks in cents (long). The vendor's SDK wants dollars (double).
// Every caller talks to the SDK directly, so every caller must remember to convert.
//
// COMPARE WITH: PaymentAdapter.java

// Third-party SDK: we can't change this class.
class AcmePayClient {
    final List<Double> charged = new ArrayList<>();

    // Returns 0 on success, a non-zero error code otherwise.
    int makePayment(String account, double amountInDollars) {
        charged.add(amountInDollars);
        return 0;
    }
}

class CheckoutService {
    private final AcmePayClient acme;
    CheckoutService(AcmePayClient acme) { this.acme = acme; }

    void pay(String customerId, long amountInCents) {
        acme.makePayment(customerId, amountInCents / 100.0);
    }
}

class SubscriptionRenewal {
    private final AcmePayClient acme;
    SubscriptionRenewal(AcmePayClient acme) { this.acme = acme; }

    void renew(String customerId, long amountInCents) {
        acme.makePayment(customerId, amountInCents); // forgot "/ 100.0" - compiles, long widens to double
    }
}

public class DirectVendorCallsBroken {
    public static void main(String[] args) {
        AcmePayClient acme = new AcmePayClient();
        new CheckoutService(acme).pay("cust-42", 1999);
        new SubscriptionRenewal(acme).renew("cust-42", 1999);

        System.out.printf("Checkout charged $%.2f%n", acme.charged.get(0));
        System.out.printf("Renewal  charged $%.2f%n", acme.charged.get(1));
        System.out.println("Same 1999 cents, charged 100 times over by the caller that forgot to convert.");
    }
}
