// Defining your own exception class. Extend RuntimeException for unchecked
// (modern default) or Exception for checked. Provide a (String) constructor
// and ideally a (String, Throwable) one so you can preserve a wrapped cause.

public class CustomException {
    public static void main(String[] args) {

        Account a = new Account();
        a.balance = 50;

        try {
            a.withdraw(100);
        } catch (InsufficientFundsException e) {
            System.out.println("caught: " + e.getMessage());
        }

        // Wrapping a lower-level cause — preserves the stack trace of the original.
        try {
            try {
                Integer.parseInt("xyz");           // throws NumberFormatException
            } catch (NumberFormatException nfe) {
                throw new InsufficientFundsException("bad amount input", nfe);
            }
        } catch (InsufficientFundsException e) {
            System.out.println("\nwith cause:");
            System.out.println("  message: " + e.getMessage());
            System.out.println("  cause:   " + e.getCause());
        }
    }
}

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}

class Account {
    double balance;

    void withdraw(double amount) {
        if (amount > balance) {
            throw new InsufficientFundsException(
                    "requested " + amount + ", balance " + balance);
        }
        balance -= amount;
    }
}
