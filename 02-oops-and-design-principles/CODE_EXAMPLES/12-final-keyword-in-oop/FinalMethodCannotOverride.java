// final on a method prevents subclasses from overriding it.
// Use it when an override would break security, ordering, or a contract.

class Account {
    final void close() {                          // can't be overridden
        System.out.println("Account.close(): logs the event, releases resources");
    }
}

class SavingsAccount extends Account {
    // @Override
    // void close() {                              // ← would not compile
    //     // ...
    // }
}

public class FinalMethodCannotOverride {
    public static void main(String[] args) {
        new SavingsAccount().close();    // always uses Account.close()
        System.out.println("Subclasses can't substitute their own close() — by design.");
    }
}
