import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Pool of size 3. We launch 3 threads that grab a connection and hold it for
// a long time, then a 4th thread tries to get one. With connectionTimeout
// set short, the 4th thread fails after a few seconds.
//
// This is the production failure mode "Connection is not available, request
// timed out after Nms". Symptoms: latency spike followed by errors.
// Causes: either pool is too small for the workload, or some code path is
// holding a connection too long (often a long-running transaction).
public class PoolExhaustion {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(3000);    // wait at most 3 seconds
        config.setPoolName("tiny-pool");

        try (HikariDataSource pool = new HikariDataSource(config)) {
            ExecutorService pool3 = Executors.newFixedThreadPool(3);

            // Three "hog" threads each grab a connection and sit on it.
            for (int i = 1; i <= 3; i++) {
                int id = i;
                pool3.submit(() -> {
                    try (Connection c = pool.getConnection()) {
                        System.out.println("Hog #" + id + " grabbed a connection.");
                        Thread.sleep(10_000);    // hold for 10 seconds
                    } catch (Exception e) {
                        System.out.println("Hog #" + id + " error: " + e.getMessage());
                    }
                });
            }

            Thread.sleep(500);   // let the hogs grab their connections first

            // Fourth caller — pool is full. Should wait up to 3 seconds, then fail.
            long start = System.nanoTime();
            try (Connection c = pool.getConnection()) {
                System.out.println("Fourth caller got a connection (unexpected!).");
            } catch (Exception e) {
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                System.out.println("Fourth caller failed after " + elapsed + " ms: " + e.getMessage());
            }

            pool3.shutdownNow();
            pool3.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
