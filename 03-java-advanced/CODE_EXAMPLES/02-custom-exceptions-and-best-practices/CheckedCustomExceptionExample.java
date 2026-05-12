class InsufficientBalanceException
        extends Exception {

    InsufficientBalanceException(
            String message
    ) {

        super(message);
    }
}

public class CheckedCustomExceptionExample {

    static void withdraw(double balance,
                         double amount)
            throws InsufficientBalanceException {

        if (amount > balance) {

            throw new InsufficientBalanceException(
                    "Insufficient Balance"
            );
        }

        System.out.println(
                "Withdrawal Successful"
        );
    }

    public static void main(String[] args) {

        try {

            withdraw(5000, 7000);

        } catch (
                InsufficientBalanceException exception
        ) {

            System.out.println(
                    exception.getMessage()
            );
        }
    }
}