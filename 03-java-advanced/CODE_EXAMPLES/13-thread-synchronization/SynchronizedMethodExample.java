class BankAccount {

    private int balance = 1000;

    synchronized void withdraw(int amount) {

        balance -= amount;

        System.out.println(
                "Remaining Balance: "
                        + balance
        );
    }
}

public class SynchronizedMethodExample {

    public static void main(String[] args) {

        BankAccount account =
                new BankAccount();

        Runnable task = () ->
                account.withdraw(100);

        new Thread(task).start();

        new Thread(task).start();
    }
}