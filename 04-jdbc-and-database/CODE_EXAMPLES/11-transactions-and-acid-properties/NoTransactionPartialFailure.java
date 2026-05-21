import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Demonstrates the disaster you get when multiple operations need to succeed
// together but you don't wrap them in a transaction.
//
// We "transfer" $100 from Alice to Bob in two steps. Between them we throw
// an exception to simulate a crash or business-rule failure. With autocommit
// ON (the default), the first UPDATE is already permanent. Money has vanished.
public class NoTransactionPartialFailure {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);
            System.out.println("Before transfer:");
            printBalances(stmt);

            try {
                // Each executeUpdate auto-commits because we haven't disabled it.
                stmt.executeUpdate("UPDATE accounts SET balance = balance - 100 WHERE owner = 'Alice'");

                // Simulate a crash, validation failure, network blip, etc.
                if (true) throw new RuntimeException("simulated failure mid-transfer");

                stmt.executeUpdate("UPDATE accounts SET balance = balance + 100 WHERE owner = 'Bob'");
            } catch (RuntimeException e) {
                System.out.println("\nFailure: " + e.getMessage());
            }

            System.out.println("\nAfter (failed) transfer:");
            printBalances(stmt);
            System.out.println("\n^ Alice's account was debited but Bob's was not.");
            System.out.println("  Money has disappeared. This is what transactions exist to prevent.");
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
