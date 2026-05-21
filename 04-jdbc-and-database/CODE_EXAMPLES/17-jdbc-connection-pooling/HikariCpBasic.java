import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Same 100 queries as 01, but via a HikariCP pool. The pool keeps connections
// open between calls, so each query reuses an existing connection instead of
// opening a fresh one.
//
// In your code, "borrowing" a connection looks identical to creating one —
// the pool sits behind the DataSource interface. You just call getConnection().
public class HikariCpBasic {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        // Seed with a plain connection.
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            for (int i = 1; i <= 100; i++) {
                s.executeUpdate("INSERT INTO users VALUES (" + i + ", 'u" + i + "')");
            }
        }

        // Build the pool.
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        config.setPoolName("demo-pool");

        // try-with-resources on the DataSource itself: closes the pool when done.
        try (HikariDataSource pool = new HikariDataSource(config)) {

            long start = System.nanoTime();
            for (int i = 1; i <= 100; i++) {
                // "Borrow" a connection from the pool. close() returns it.
                try (Connection conn = pool.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT name FROM users WHERE id = ?")) {
                    ps.setInt(1, i);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                    }
                }
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("100 queries via pool: " + elapsedMs + " ms");
        }
    }
}
