import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Observer: OrderService only announces "an order was placed". Whoever cares subscribes.
// OrderService no longer knows email or loyalty exist, and each entry point publishes once.
// Spring's ApplicationEventPublisher + @EventListener is this pattern (module 07, topic 11).
//
// COMPARE WITH: DirectNotificationsBroken.java

class OrderEvents {
    private final List<Consumer<String>> listeners = new ArrayList<>();
    void subscribe(Consumer<String> listener) { listeners.add(listener); }
    void publishOrderPlaced(String orderId) { listeners.forEach(listener -> listener.accept(orderId)); }
}

class EmailService { final List<String> sent = new ArrayList<>(); void confirm(String id) { sent.add(id); } }
class LoyaltyService { int points = 0; void award(String id) { points += 10; } }

class OrderService {
    private final OrderEvents events;
    OrderService(OrderEvents events) { this.events = events; }

    void placeOrder(String id) { events.publishOrderPlaced(id); }
    void placeOrderFromCart(String id) { events.publishOrderPlaced(id); }
}

public class ObserverPattern {
    public static void main(String[] args) {
        OrderEvents events = new OrderEvents();
        EmailService email = new EmailService();
        LoyaltyService loyalty = new LoyaltyService();

        // Wiring happens once, at startup. Adding a new reaction = one more subscribe line.
        events.subscribe(email::confirm);
        events.subscribe(loyalty::award);

        OrderService orders = new OrderService(events);
        orders.placeOrder("order-1");
        orders.placeOrderFromCart("order-2");

        System.out.println("Confirmation emails: " + email.sent.size());
        System.out.println("Loyalty points:      " + loyalty.points);
        if (email.sent.size() != 2 || loyalty.points != 20) throw new AssertionError("a listener missed an order");
    }
}
