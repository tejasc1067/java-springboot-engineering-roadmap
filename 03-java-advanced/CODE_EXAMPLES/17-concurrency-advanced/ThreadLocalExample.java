public class ThreadLocalExample {

    private static final ThreadLocal<String>
            requestId =
            new ThreadLocal<>();

    public static void main(String[] args) {

        Runnable task = () -> {

            requestId.set(
                    "REQ-"
                            + Thread.currentThread().getId()
            );

            System.out.println(
                    Thread.currentThread().getName()
                            + " -> "
                            + requestId.get()
            );

            requestId.remove();
        };

        new Thread(task).start();

        new Thread(task).start();
    }
}