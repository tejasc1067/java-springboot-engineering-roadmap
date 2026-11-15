import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Strategy: the varying rule is an object (here, a lambda) handed to the code that uses it.
// Checkout no longer knows any discount rules, so adding one never touches Checkout.
//
// COMPARE WITH: DiscountIfElseBroken.java

@FunctionalInterface
interface DiscountStrategy {
    long apply(long priceInCents);
}

class Checkout {
    long total(long priceInCents, DiscountStrategy discount) {
        return discount.apply(priceInCents);
    }
}

public class StrategyPattern {
    // Choosing a strategy by name still needs ONE lookup - and it fails loudly on unknown names.
    static final Map<String, DiscountStrategy> DISCOUNTS = Map.of(
            "REGULAR", price -> price,
            "STUDENT", price -> price * 90 / 100,
            "BLACK_FRIDAY", price -> price * 70 / 100,
            "EMPLOYEE", price -> price * 80 / 100);  // the new rule: one line, Checkout untouched

    static DiscountStrategy discountFor(String customerType) {
        DiscountStrategy strategy = DISCOUNTS.get(customerType);
        if (strategy == null) throw new IllegalArgumentException("no discount rule for " + customerType);
        return strategy;
    }

    public static void main(String[] args) {
        Checkout checkout = new Checkout();
        long employee = checkout.total(10_000, discountFor("EMPLOYEE"));
        System.out.println("EMPLOYEE pays " + employee);
        if (employee != 8000) throw new AssertionError(employee);

        try {
            discountFor("EMPLOYE"); // typo
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown type fails loudly: " + e.getMessage());
        }

        // The JDK's best-known strategy: Comparator. sort() is the fixed algorithm; the order is the strategy.
        List<String> names = new ArrayList<>(List.of("Charlie", "al", "Bob", "dan"));
        names.sort(Comparator.naturalOrder());
        System.out.println("natural order:    " + names);
        names.sort(Comparator.comparing(String::length));
        System.out.println("by length:        " + names);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        System.out.println("case-insensitive: " + names);
    }
}
