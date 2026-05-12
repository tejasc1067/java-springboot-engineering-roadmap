public class ThreadSleepExample {

    public static void main(String[] args)
            throws InterruptedException {

        System.out.println(
                "Thread started"
        );

        Thread.sleep(2000);

        System.out.println(
                "Thread resumed after sleep"
        );
    }
}