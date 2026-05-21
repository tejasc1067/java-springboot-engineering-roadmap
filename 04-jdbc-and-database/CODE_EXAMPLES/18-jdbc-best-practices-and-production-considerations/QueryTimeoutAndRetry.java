import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Two production patterns:
//   1. setQueryTimeout — cap how long a single statement is allowed to run.
//   2. Retry with backoff — only for transient failures (connection blip,
//      deadlock). Never retry constraint violations or syntax errors.
public class QueryTimeoutAndRetry {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            s.executeUpdate("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        }

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {

            // Cap this query at 5 seconds. A runaway query (missing index, accidental
            // cross join) raises a SQLException after the timeout instead of locking
            // up the request thread indefinitely.
            ps.setQueryTimeout(5);
            ps.setInt(1, 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("With 5s timeout, query returned: " + rs.getString("name"));
                }
            }
        }

        // Retry pattern: tolerate up to 3 transient failures.
        // Simulated by a function that fails twice then succeeds.
        String result = withRetry(3, () -> {
            // pretend this is a real query that occasionally fails on connection blips
            return simulatedFlakeyQuery();
        });
        System.out.println("After retries, query succeeded: " + result);
    }

    private static int attempts = 0;
    private static String simulatedFlakeyQuery() throws SQLException {
        attempts++;
        if (attempts < 3) {
            throw new SQLException("connection lost", "08006");  // SQLState class 08 = connection
        }
        return "ok";
    }

    private static String withRetry(int maxAttempts, SqlCallable<String> work) throws Exception {
        SQLException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return work.call();
            } catch (SQLException e) {
                if (!isTransient(e)) throw e;     // non-transient → fail immediately
                last = e;
                long delayMs = (long) Math.pow(2, attempt - 1) * 100;
                System.out.printf("Attempt %d failed (%s). Retrying in %d ms.%n",
                        attempt, e.getMessage(), delayMs);
                Thread.sleep(delayMs);
            }
        }
        throw last;
    }

    // Crude "is this a transient error" check. Real apps consult a list of
    // SQLState codes that match their specific database.
    private static boolean isTransient(SQLException e) {
        String state = e.getSQLState();
        if (state == null) return false;
        return state.startsWith("08")    // connection errors
            || state.equals("40001");    // serialization failure
    }

    @FunctionalInterface
    interface SqlCallable<T> { T call() throws SQLException; }
}
