// Anti-example: public fields. The class has rules ("balance can't be negative")
// but no way to enforce them. Outside code casually breaks them.
//
// Compare with EncapsulatedAccount.java for the fix.

class BankAccount {
    public String owner;
    public double balance;
}

public class PublicFieldsBroken {
    public static void main(String[] args) {
        BankAccount a = new BankAccount();
        a.owner = "Alice";
        a.balance = 1000;
        System.out.println(a.owner + " has $" + a.balance);

        // Nothing stops any of this:
        a.balance = -999_999;       // negative balance — impossible in reality
        a.owner = null;             // every later access becomes NPE territory

        System.out.println("After corruption: " + a.owner + " has $" + a.balance);
        System.out.println("The class has no way to prevent this. Hence: encapsulation.");
    }
}
