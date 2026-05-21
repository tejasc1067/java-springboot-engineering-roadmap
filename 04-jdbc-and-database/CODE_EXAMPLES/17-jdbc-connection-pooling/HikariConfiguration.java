import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;

// Reference: the configuration knobs you'll actually touch in production,
// with sensible defaults for a typical small-to-medium web app.
public class HikariConfiguration {

    public static void main(String[] args) throws Exception {
        HikariConfig c = new HikariConfig();

        // --- Required ---
        c.setJdbcUrl("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");
        c.setUsername("sa");
        c.setPassword("");

        // --- Sizing ---
        // Maximum connections this pool will keep open at once.
        // 10 handles a surprising amount of traffic. Don't oversize blindly.
        c.setMaximumPoolSize(10);
        // HikariCP recommends minimumIdle == maximumPoolSize for production
        // (fewer connection-creation spikes under sudden load).
        c.setMinimumIdle(10);

        // --- Timeouts ---
        // How long getConnection() will wait for a free slot before throwing.
        // 30s is the default; for an interactive web app, 5-10s is more user-friendly.
        c.setConnectionTimeout(10_000);
        // Close idle connections after this many ms.
        c.setIdleTimeout(10 * 60 * 1000);    // 10 minutes
        // Force-recycle connections at this age, even if active.
        // Some databases drop long-lived connections; 30 min avoids the issue.
        c.setMaxLifetime(30 * 60 * 1000);
        // In dev: detect when a borrowed connection isn't returned within 60s
        // and log a warning with stack trace. Off by default (set to 0).
        c.setLeakDetectionThreshold(60_000);

        // --- Identification (helpful in logs) ---
        c.setPoolName("api-pool");

        try (HikariDataSource pool = new HikariDataSource(c)) {
            System.out.println("Pool created with:");
            System.out.println("  maximumPoolSize        = " + c.getMaximumPoolSize());
            System.out.println("  minimumIdle            = " + c.getMinimumIdle());
            System.out.println("  connectionTimeout      = " + c.getConnectionTimeout() + " ms");
            System.out.println("  idleTimeout            = " + c.getIdleTimeout() + " ms");
            System.out.println("  maxLifetime            = " + c.getMaxLifetime() + " ms");
            System.out.println("  leakDetectionThreshold = " + c.getLeakDetectionThreshold() + " ms");

            try (Connection conn = pool.getConnection()) {
                System.out.println("\nGot a connection. Pool ready.");
            }
        }
    }
}
