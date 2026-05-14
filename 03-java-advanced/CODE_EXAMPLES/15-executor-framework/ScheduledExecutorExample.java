import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScheduledExecutorExample {

    public static void main(String[] args) {

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(1);

        scheduler.schedule(() -> {

            System.out.println(
                    "Scheduled Task Executed"
            );

        }, 2, TimeUnit.SECONDS);

        scheduler.shutdown();
    }
}