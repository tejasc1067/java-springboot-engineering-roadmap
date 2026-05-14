import java.util.concurrent.Semaphore;

class DatabaseConnection {

    private final Semaphore semaphore =
            new Semaphore(2);

    void accessDatabase() {

        try {

            semaphore.acquire();

            System.out.println(
                    Thread.currentThread().getName()
                            + " accessing database"
            );

            Thread.sleep(1000);

        } catch (InterruptedException exception) {

            exception.printStackTrace();

        } finally {

            semaphore.release();
        }
    }
}

public class SemaphoreExample {

    public static void main(String[] args) {

        DatabaseConnection connection =
                new DatabaseConnection();

        Runnable task =
                connection::accessDatabase;

        new Thread(task).start();

        new Thread(task).start();

        new Thread(task).start();
    }
}