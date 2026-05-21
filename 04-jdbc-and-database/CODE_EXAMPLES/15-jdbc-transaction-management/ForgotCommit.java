import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Anti-example. We disable autocommit, run an UPDATE, then forget to commit.
// When the connection closes, the driver implicitly rolls back. The update
// vanishes. Silent data loss.
//
// COMPARE WITH: RollbackOnException.java — same pattern, with explicit
// commit on success.
public class ForgotCommit {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        // First connection — disables autocommit, makes a change, never commits.
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE accounts (owner VARCHAR(20), balance INT)");
            // The CREATE above is DDL — H2 commits DDL implicitly. Below is the bug.

            conn.setAutoCommit(false);
            stmt.executeUpdate("INSERT INTO accounts VALUES ('Alice', 100)");
            // ← we forget to call conn.commit() here

            // Inside this connection we can still see the row.
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rs.next();
                System.out.println("Inside the same connection, row count: " + rs.getInt(1));
            }
            // connection closes here without commit → rollback happens silently
        }

        // Second, fresh connection — the row is gone.
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM accounts")) {
            rs.next();
            System.out.println("From a fresh connection, row count: " + rs.getInt(1));
            System.out.println("  → the INSERT silently vanished. Always commit (or explicitly rollback).");
        }
    }
}
