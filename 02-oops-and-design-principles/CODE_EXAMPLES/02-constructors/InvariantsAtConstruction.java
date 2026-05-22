// The real power of constructors: enforce invariants. Make invalid objects
// impossible to create.
//
// Without validation, "broken" Accounts (blank owner, negative balance) can be
// constructed and silently passed around. Every later method has to defend.
// With validation in the constructor, those bad states cannot exist.

class Account {
    String owner;
    double balance;

    Account(String owner, double initialBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("initial balance cannot be negative");
        }
        this.owner = owner;
        this.balance = initialBalance;
    }

    @Override
    public String toString() {
        return owner + " has $" + balance;
    }
}

public class InvariantsAtConstruction {
    public static void main(String[] args) {

        // Valid: works fine.
        Account good = new Account("Alice", 100.00);
        System.out.println(good);

        // Each of the following deliberately violates an invariant.
        attempt(() -> new Account(null,    100.00));   // null owner
        attempt(() -> new Account("",      100.00));   // blank owner
        attempt(() -> new Account("Bob",  -50.00));    // negative balance

        System.out.println("\nNo invalid Account was ever created. Downstream code");
        System.out.println("can trust that every Account has a real owner and a non-negative balance.");
    }

    private static void attempt(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
    }
}
