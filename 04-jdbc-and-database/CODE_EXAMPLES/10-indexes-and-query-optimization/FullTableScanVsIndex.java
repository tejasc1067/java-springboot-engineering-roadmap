import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Inserts 50,000 rows, then runs the same SELECT twice — first with no index
// on the column we're filtering, then after adding an index.
//
// On H2 (an in-memory database), the difference is smaller than on disk-based
// systems, but the index version is still measurably faster. Bigger tables
// magnify the gap.
public class FullTableScanVsIndex {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";
    private static final int ROW_COUNT = 50_000;
    private static final String TARGET = "user_42000@example.com";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(50),
                    email VARCHAR(100)
                )
                """);

            // Use a PreparedStatement + manual transaction for fast bulk insert.
            // (More on this in topics 14 and 16.)
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, name, email) VALUES (?, ?, ?)")) {
                for (int i = 1; i <= ROW_COUNT; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "user_" + i);
                    ps.setString(3, "user_" + i + "@example.com");
                    ps.addBatch();
                    if (i % 1000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(true);

            System.out.println("Inserted " + ROW_COUNT + " rows.");

            // Run 1: no index on `email`. Database must scan all rows.
            long t1 = time(stmt, "SELECT * FROM users WHERE email = '" + TARGET + "'");
            System.out.printf("Without index:  %dµs%n", t1);

            // Add the index.
            stmt.executeUpdate("CREATE INDEX idx_users_email ON users(email)");

            // Run 2: same query, but now the database can use the index.
            long t2 = time(stmt, "SELECT * FROM users WHERE email = '" + TARGET + "'");
            System.out.printf("With index:     %dµs%n", t2);

            if (t2 > 0) {
                System.out.printf("Speedup:        %.1fx%n", (double) t1 / t2);
            }
        }
    }

    // Runs the query a few times and returns the median time, to reduce JIT noise.
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
