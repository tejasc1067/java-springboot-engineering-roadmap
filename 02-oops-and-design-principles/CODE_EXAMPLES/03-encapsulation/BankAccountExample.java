class BankAccount {

    private double balance;

    public void deposit(double amount) {

        if (amount > 0) {

            balance += amount;

            System.out.println(
                    "Amount Deposited: "
                            + amount
            );
        }
    }

    public void withdraw(double amount) {

        if (amount > 0 && amount <= balance) {

            balance -= amount;

            System.out.println(
                    "Amount Withdrawn: "
                            + amount
            );
        }
        else {

            System.out.println(
                    "Invalid Withdrawal"
            );
        }
    }

    public double getBalance() {

        return balance;
    }
}

public class BankAccountExample {

    public static void main(String[] args) {

        BankAccount account = new BankAccount();

        account.deposit(5000);

        account.withdraw(2000);

        System.out.println(
                "Current Balance: "
                        + account.getBalance()
        );
    }
}