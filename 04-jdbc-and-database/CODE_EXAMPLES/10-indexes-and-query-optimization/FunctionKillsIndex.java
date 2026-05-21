import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Even with an index, wrapping the column in a function bypasses it.
// `WHERE email = 'X'` can use idx_email. `WHERE LOWER(email) = 'x'` cannot.
//
// The database can't tell which (potentially millions of) rows would produce
// the matching LOWER() result without computing it for every row first.
//
// Fixes (pick one):
//   1. Store the value already-normalized (always lowercase emails on insert).
//   2. Create a functional index: CREATE INDEX ... ON users(LOWER(email)).
public class FunctionKillsIndex {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, email VARCHAR(100))");

            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users VALUES (?, ?)")) {
                for (int i = 1; i <= 50_000; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "User_" + i + "@Example.com");  // mixed case
                    ps.addBatch();
                    if (i % 1000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(true);

            stmt.executeUpdate("CREATE INDEX idx_users_email ON users(email)");

            // 1. Direct comparison — uses the index. Fast.
            long t1 = time(stmt, "SELECT * FROM users WHERE email = 'User_42000@Example.com'");
            System.out.printf("Direct match (uses index):     %dµs%n", t1);

            // 2. LOWER() on the indexed column — can't use the index. Slow.
            long t2 = time(stmt, "SELECT * FROM users WHERE LOWER(email) = 'user_42000@example.com'");
            System.out.printf("LOWER(email) match (no index): %dµs%n", t2);

            if (t1 > 0) {
                System.out.printf("Slowdown from function: %.1fx%n", (double) t2 / t1);
            }
        }
    }

    private static long time(Statement stmt, String sql) throws Exception {
        long[] times = new long[5];
        for (int i = 0; i < times.length; i++) {
            long start = System.nanoTime();
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) { /* consume */ }
            }
            times[i] = (System.nanoTime() - start) / 1_000;
        }
        java.util.Arrays.sort(times);
        return times[times.length / 2];
    }
}
