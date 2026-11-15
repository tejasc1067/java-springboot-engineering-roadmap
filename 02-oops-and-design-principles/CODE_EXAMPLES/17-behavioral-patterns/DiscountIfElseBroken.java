// Checkout knows every discount rule, chosen by a string in an if/else chain.
// The sign-up team adds an "EMPLOYEE" customer type; nobody tells Checkout.
//
// COMPARE WITH: StrategyPattern.java

class Checkout {
    long total(long priceInCents, String customerType) {
        if (customerType.equals("STUDENT")) {
            return priceInCents * 90 / 100;
        } else if (customerType.equals("BLACK_FRIDAY")) {
            return priceInCents * 70 / 100;
        } else {
            return priceInCents; // everything unknown silently lands here
        }
    }
}

public class DiscountIfElseBroken {
    public static void main(String[] args) {
        Checkout checkout = new Checkout();
        System.out.println("STUDENT  pays " + checkout.total(10_000, "STUDENT"));
        long employee = checkout.total(10_000, "EMPLOYEE"); // employees were promised 20% off
        System.out.println("EMPLOYEE pays " + employee + " (promised 8000)");
        System.out.println("No error, no warning. Every employee order is overcharged until someone complains.");
    }
}
