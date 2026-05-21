import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// What happens when one row in the middle of a batch fails (e.g. duplicate
// primary key)? You get a BatchUpdateException.
//
// The "after the failure, what's actually inserted" behavior varies by driver
// and by whether you're in a transaction. The safest pattern is:
//   1. Wrap the batch in a transaction.
//   2. On BatchUpdateException, rollback the whole thing.
//   3. Inspect the exception's updateCounts to log which row failed.
//   4. Fix the input and retry.
public class PartialFailureHandling {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
                s.executeUpdate("INSERT INTO users VALUES (5, 'existing')");  // pre-existing row
            }

            // Try to insert 10 rows. Row #5 will collide with the existing row.
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users VALUES (?, ?)")) {

                for (int i = 1; i <= 10; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "u" + i);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                System.out.println("All inserted (unexpected).");

            } catch (BatchUpdateException e) {
                conn.rollback();
                System.out.println("Batch failed: " + e.getMessage());

                int[] counts = e.getUpdateCounts();
                System.out.println("Per-row outcome (-3 = EXECUTE_FAILED):");
                for (int i = 0; i < counts.length; i++) {
                    System.out.printf("  row index %d (id=%d): count=%d%n",
                            i, i + 1, counts[i]);
                }
            } finally {
                conn.setAutoCommit(true);
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                System.out.println("\nFinal row count: " + rs.getInt(1) + "  (only the pre-existing row)");
            }
        }
    }
}
