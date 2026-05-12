class Inventory {

    private int stock = 10;

    private final Object lock =
            new Object();

    void purchase() {

        synchronized (lock) {

            if (stock > 0) {

                stock--;

                System.out.println(
                        "Stock Remaining: "
                                + stock
                );
            }
        }
    }
}

public class SynchronizedBlockExample {

    public static void main(String[] args) {

        Inventory inventory =
                new Inventory();

        Runnable task =
                inventory::purchase;

        new Thread(task).start();

        new Thread(task).start();
    }
}