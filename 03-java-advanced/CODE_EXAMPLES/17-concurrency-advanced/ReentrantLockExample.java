import java.util.concurrent.locks.ReentrantLock;

class SharedPrinter {

    private final ReentrantLock lock =
            new ReentrantLock();

    void print(String message) {

        lock.lock();

        try {

            System.out.println(
                    "Printing: " + message
            );

        } finally {

            lock.unlock();
        }
    }
}

public class ReentrantLockExample {

    public static void main(String[] args) {

        SharedPrinter printer =
                new SharedPrinter();

        Runnable task = () ->
                printer.print("Document");

        new Thread(task).start();

        new Thread(task).start();
    }
}