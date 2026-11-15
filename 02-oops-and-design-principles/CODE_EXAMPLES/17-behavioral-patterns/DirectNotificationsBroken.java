import java.util.ArrayList;
import java.util.List;

// OrderService calls every follow-up action itself. Orders can be placed from two entry points,
// so the list of follow-ups is copied into both. Loyalty points were added to only one.
//
// COMPARE WITH: ObserverPattern.java

class EmailService { final List<String> sent = new ArrayList<>(); void confirm(String id) { sent.add(id); } }
class LoyaltyService { int points = 0; void award(String id) { points += 10; } }

class OrderService {
    private final EmailService email;
    private final LoyaltyService loyalty;
    OrderService(EmailService email, LoyaltyService loyalty) { this.email = email; this.loyalty = loyalty; }

    void placeOrder(String id) {
        email.confirm(id);
        loyalty.award(id);
    }

    void placeOrderFromCart(String id) {
        email.confirm(id);
        // loyalty.award(id) - added later to placeOrder, forgotten here
    }
}

public class DirectNotificationsBroken {
    public static void main(String[] args) {
        EmailService email = new EmailService();
        LoyaltyService loyalty = new LoyaltyService();
        OrderService orders = new OrderService(email, loyalty);

        orders.placeOrder("order-1");
        orders.placeOrderFromCart("order-2");

        System.out.println("Confirmation emails: " + email.sent.size());
        System.out.println("Loyalty points:      " + loyalty.points + " (expected 20)");
        System.out.println("Every cart checkout silently earns nothing.");
    }
}
