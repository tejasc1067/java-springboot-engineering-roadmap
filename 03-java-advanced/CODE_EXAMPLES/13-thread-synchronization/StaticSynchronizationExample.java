class GlobalCounter {

    private static int count = 0;

    static synchronized void increment() {

        count++;

        System.out.println(
                "Count: " + count
        );
    }
}

public class StaticSynchronizationExample {

    public static void main(String[] args) {

        Runnable task =
                GlobalCounter::increment;

        new Thread(task).start();

        new Thread(task).start();
    }
}