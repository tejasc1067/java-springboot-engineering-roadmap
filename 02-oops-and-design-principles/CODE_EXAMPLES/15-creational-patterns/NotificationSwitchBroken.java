// "Which class do I create?" decided with a switch - copied into every place that needs it.
// SMS support was added to send() but not to costInCents(). It compiles. It fails at runtime.
//
// COMPARE WITH: NotificationFactory.java

interface Notifier {
    String send(String to, String message);
    int costInCents();
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
        Notifier notifier = switch (channel) {          // copy #1 of the decision
            case "email" -> new EmailNotifier();
            case "sms" -> new SmsNotifier();            // added when SMS launched
            default -> throw new IllegalArgumentException("unknown channel " + channel);
        };
        return notifier.send(to, "Your order shipped");
    }
}

class BillingReport {
    int monthlyCost(String channel, int messages) {
        Notifier notifier = switch (channel) {          // copy #2 of the same decision
            case "email" -> new EmailNotifier();
            default -> throw new IllegalArgumentException("unknown channel " + channel); // nobody updated this one
        };
        return notifier.costInCents() * messages;
    }
}

public class NotificationSwitchBroken {
    public static void main(String[] args) {
        System.out.println(new OrderService().notifyCustomer("sms", "+44 7700 900123"));
        try {
            System.out.println(new BillingReport().monthlyCost("sms", 1000));
        } catch (IllegalArgumentException e) {
            System.out.println("Billing report crashed: " + e.getMessage());
            System.out.println("Same decision, written twice, updated once.");
        }
    }
}
