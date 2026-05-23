// A public mutable field is a permanent escape hatch — no validation possible.
// A private field with a validating setter lets the class defend its invariants.

public class EncapsulationGoodAndBad {
    public static void main(String[] args) {

        // BAD: nothing stops nonsense.
        BadAccount bad = new BadAccount();
        bad.balance = 100;
        bad.balance = -50;            // negative balance, sure
        bad.balance = Double.NaN;     // also fine, apparently
        System.out.println("BadAccount.balance  = " + bad.balance);

        // GOOD: the class enforces its rules.
        GoodAccount good = new GoodAccount();
        good.deposit(100);
        good.withdraw(30);
        System.out.println("GoodAccount.balance = " + good.getBalance());

        // Try to abuse it:
        try {
            good.withdraw(1000);
        } catch (IllegalStateException e) {
            System.out.println("blocked withdraw: " + e.getMessage());
        }
        try {
            good.deposit(-10);
        } catch (IllegalArgumentException e) {
            System.out.println("blocked deposit:  " + e.getMessage());
        }
    }
}

class BadAccount {
    public double balance;          // anyone can write anything
}

class GoodAccount {
    private double balance;          // hidden state

    public double getBalance() {
        return balance;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("deposit must be positive");
        balance += amount;
    }

    public void withdraw(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("withdraw must be positive");
        if (amount > balance) throw new IllegalStateException("insufficient funds");
        balance -= amount;
    }
}
