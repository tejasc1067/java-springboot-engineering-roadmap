// The "which class?" decision lives in ONE method. Callers ask for a Notifier and
// never write `new EmailNotifier()` themselves, so adding a channel is a one-place change.
//
// COMPARE WITH: NotificationSwitchBroken.java

interface Notifier {
    String send(String to, String message);
    int costInCents();

    // Factory method: a static method that decides which implementation to create.
    static Notifier forChannel(String channel) {
        return switch (channel) {
            case "email" -> new EmailNotifier();
            case "sms" -> new SmsNotifier();
            default -> throw new IllegalArgumentException("unknown channel " + channel);
        };
    }
}

class EmailNotifier implements Notifier {
    public String send(String to, String message) { return "EMAIL to " + to + ": " + message; }
    public int costInCents() { return 0; }
}

class SmsNotifier implements Notifier {
    public String send(String to, String message) { return "SMS to " + to + ": " + message; }
    public int costInCents() { return 5; }
}

class OrderService {
    String notifyCustomer(String channel, String to) {
        return Notifier.forChannel(channel).send(to, "Your order shipped");
    }
}

class BillingReport {
    int monthlyCost(String channel, int messages) {
        return Notifier.forChannel(channel).costInCents() * messages;
    }
}

public class NotificationFactory {
    public static void main(String[] args) {
        System.out.println(new OrderService().notifyCustomer("sms", "+44 7700 900123"));
        int cost = new BillingReport().monthlyCost("sms", 1000);
        System.out.println("SMS cost for 1000 messages: " + cost + " cents");
        if (cost != 5000) throw new AssertionError("expected 5000, got " + cost);
        System.out.println("Both callers support SMS because only the factory had to learn about it.");
    }
}
