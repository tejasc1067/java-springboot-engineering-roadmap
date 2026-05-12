public class DeadlockExample {

    private static final Object lockOne =
            new Object();

    private static final Object lockTwo =
            new Object();

    public static void main(String[] args) {

        Thread threadOne =
                new Thread(() -> {

                    synchronized (lockOne) {

                        System.out.println(
                                "Thread-1 acquired lockOne"
                        );

                        synchronized (lockTwo) {

                            System.out.println(
                                    "Thread-1 acquired lockTwo"
                            );
                        }
                    }
                });

        Thread threadTwo =
                new Thread(() -> {

                    synchronized (lockTwo) {

                        System.out.println(
                                "Thread-2 acquired lockTwo"
                        );

                        synchronized (lockOne) {

                            System.out.println(
                                    "Thread-2 acquired lockOne"
                            );
                        }
                    }
                });

        threadOne.start();

        threadTwo.start();
    }
}