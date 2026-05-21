import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// EXPLAIN shows the database's plan for executing a query. Without committing
// to memorizing every column of EXPLAIN output, you can learn to read:
//   - "tableScan" / "Seq Scan" = reading every row, bad on big tables
//   - "indexScan" / "Index Scan" = using an index, good
public class ExplainPlan {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, email VARCHAR(100))");

            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users VALUES (?, ?)")) {
                for (int i = 1; i <= 10_000; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "u" + i + "@x.com");
                    ps.addBatch();
                    if (i % 1000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(true);

            // Plan BEFORE the index — expect a full table scan on `email`.
            System.out.println("--- EXPLAIN before index (filtering by email) ---");
            explain(stmt, "SELECT * FROM users WHERE email = 'u500@x.com'");

            // Plan BEFORE the index — but filtering by the PK, which is always indexed.
            System.out.println("\n--- EXPLAIN (filtering by primary key) ---");
            explain(stmt, "SELECT * FROM users WHERE id = 500");

            // Add an index on email.
            stmt.executeUpdate("CREATE INDEX idx_users_email ON users(email)");

            // Plan AFTER the index — expect an index lookup.
            System.out.println("\n--- EXPLAIN after index on email ---");
            explain(stmt, "SELECT * FROM users WHERE email = 'u500@x.com'");
        }
    }

    private static void explain(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
            while (rs.next()) {
                // The single column varies by DB; H2 returns the plan as a string.
                System.out.println("  " + rs.getString(1));
            }
        }
    }
}
