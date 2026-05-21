import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Same scenario as 01, but wrapped in a transaction.
// When the simulated failure happens, ROLLBACK undoes the first UPDATE.
// The database is back to its initial consistent state.
//
// This is the pattern every multi-step business operation should follow.
public class WithTransactionRolledBack {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);
            System.out.println("Before transfer:");
            printBalances(stmt);

            // Turn off autocommit. Statements are now tentative until we commit.
            conn.setAutoCommit(false);

            try {
                stmt.executeUpdate("UPDATE accounts SET balance = balance - 100 WHERE owner = 'Alice'");
                if (true) throw new RuntimeException("simulated failure mid-transfer");
                stmt.executeUpdate("UPDATE accounts SET balance = balance + 100 WHERE owner = 'Bob'");
                conn.commit();
            } catch (RuntimeException e) {
                conn.rollback();   // Undo the first UPDATE
                System.out.println("\nFailure: " + e.getMessage() + " — rolled back.");
            } finally {
                conn.setAutoCommit(true);  // Restore default for any later code
            }

            System.out.println("\nAfter (failed but rolled-back) transfer:");
            printBalances(stmt);
            System.out.println("\n^ Both balances unchanged. Failure left no trace.");
        }
    }

    private static void setup(Statement stmt) throws Exception {
        stmt.executeUpdate("CREATE TABLE accounts (owner VARCHAR(20) PRIMARY KEY, balance DECIMAL(10, 2))");
        stmt.executeUpdate("INSERT INTO accounts VALUES ('Alice', 500), ('Bob', 200)");
    }

    private static void printBalances(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT owner, balance FROM accounts ORDER BY owner")) {
            while (rs.next()) {
                System.out.printf("  %-6s %.2f%n", rs.getString("owner"), rs.getBigDecimal("balance"));
            }
        }
    }
}
