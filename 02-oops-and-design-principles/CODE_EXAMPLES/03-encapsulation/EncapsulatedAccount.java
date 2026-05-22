// The fix. Same data, but the class controls how it's accessed and modified.
// Negative balance is now structurally impossible — there's no code path that
// can produce it.

class BankAccount {
    private String owner;
    private double balance;

    BankAccount(String owner, double initialBalance) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner required");
        if (initialBalance < 0)                throw new IllegalArgumentException("initial balance cannot be negative");
        this.owner = owner;
        this.balance = initialBalance;
    }

    void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("deposit must be positive");
        balance += amount;
    }

    void withdraw(double amount) {
        if (amount <= 0)         throw new IllegalArgumentException("withdrawal must be positive");
        if (amount > balance)    throw new IllegalStateException("insufficient funds");
        balance -= amount;
    }

    double getBalance() { return balance; }
    String getOwner()   { return owner; }
}

public class EncapsulatedAccount {
    public static void main(String[] args) {
        BankAccount a = new BankAccount("Alice", 1000);
        a.deposit(500);
        a.withdraw(200);
        System.out.println(a.getOwner() + " now has $" + a.getBalance());

        // Now try to break it the way we broke PublicFieldsBroken:
        attempt(() -> a.withdraw(5_000));         // insufficient funds
        attempt(() -> a.deposit(-100));            // negative deposit
        // a.balance = -999_999;                    // ← won't even compile: balance is private
    }

    private static void attempt(Runnable r) {
        try { r.run(); }
        catch (Exception e) {
            System.out.println("Rejected: " + e.getMessage());
        }
    }
}
