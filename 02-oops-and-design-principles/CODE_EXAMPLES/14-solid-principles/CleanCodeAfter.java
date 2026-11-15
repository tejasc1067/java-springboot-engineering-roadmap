import java.util.List;

// Same rules as CleanCodeBefore, written so the call site can't be misread.
// No new classes beyond a record; no patterns. Just names.

record LineItem(double unitPrice, int quantity) {
    double subtotal() { return unitPrice * quantity; }
}

public class CleanCodeAfter {

    static final double MEMBER_DISCOUNT_RATE = 0.10;
    static final double SHIPPING_CREDIT_THRESHOLD = 100.0;
    static final double SHIPPING_CREDIT = 5.0;

    // Two methods instead of one boolean: the caller has to say which one it means.
    static double totalForGuest(List<LineItem> items) {
        return applyShippingCredit(subtotal(items));
    }

    static double totalForMember(List<LineItem> items) {
        double discounted = subtotal(items) * (1 - MEMBER_DISCOUNT_RATE);
        return applyShippingCredit(discounted);
    }

    private static double subtotal(List<LineItem> items) {
        return items.stream().mapToDouble(LineItem::subtotal).sum();
    }

    private static double applyShippingCredit(double amount) {
        return amount > SHIPPING_CREDIT_THRESHOLD ? amount - SHIPPING_CREDIT : amount;
    }

    public static void main(String[] args) {
        List<LineItem> items = List.of(new LineItem(40.0, 2), new LineItem(25.0, 1));

        double guestTotal = totalForGuest(items);
        double memberTotal = totalForMember(items);

        System.out.println("Guest total:  " + guestTotal);
        System.out.println("Member total: " + memberTotal);
        if (guestTotal != 100.0) throw new AssertionError("guest total wrong: " + guestTotal);
        if (memberTotal != 94.5) throw new AssertionError("member total wrong: " + memberTotal);
        System.out.println("The discount rate lives in a named constant, so there is no comment to go stale.");
    }
}
