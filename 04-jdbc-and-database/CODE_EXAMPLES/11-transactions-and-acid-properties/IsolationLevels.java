import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Quick reference: how to set and inspect isolation levels in JDBC.
// Full isolation-anomaly demos require multiple connections running
// concurrently — we set that up below for the simplest case (a "non-repeatable
// read"): one transaction reads a row twice, with another transaction
// updating it in between.
public class IsolationLevels {

    private static final String URL = "jdbc:h2:mem:iso;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        // Setup with one connection.
        try (Connection setup = DriverManager.getConnection(URL, "sa", "");
             Statement s = setup.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS accounts");
            s.executeUpdate("CREATE TABLE accounts (id INT PRIMARY KEY, balance INT)");
            s.executeUpdate("INSERT INTO accounts VALUES (1, 1000)");
        }

        // Demonstrate constant names for isolation levels.
        System.out.println("Available isolation levels (JDBC constants):");
        System.out.println("  TRANSACTION_READ_UNCOMMITTED = " + Connection.TRANSACTION_READ_UNCOMMITTED);
        System.out.println("  TRANSACTION_READ_COMMITTED   = " + Connection.TRANSACTION_READ_COMMITTED);
        System.out.println("  TRANSACTION_REPEATABLE_READ  = " + Connection.TRANSACTION_REPEATABLE_READ);
        System.out.println("  TRANSACTION_SERIALIZABLE     = " + Connection.TRANSACTION_SERIALIZABLE);

        // Two connections simulate two concurrent transactions.
        try (Connection reader = DriverManager.getConnection(URL, "sa", "");
             Connection writer = DriverManager.getConnection(URL, "sa", "")) {

            // Make the reader use READ_COMMITTED (typical default).
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            reader.setAutoCommit(false);

            int firstRead = readBalance(reader);
            System.out.println("\nReader (READ COMMITTED) first read: " + firstRead);

            // Writer changes the value and commits.
            writer.setAutoCommit(true);
            try (Statement s = writer.createStatement()) {
                s.executeUpdate("UPDATE accounts SET balance = 500 WHERE id = 1");
            }

            // Reader, still in the same transaction, reads again.
            int secondRead = readBalance(reader);
            System.out.println("Reader (READ COMMITTED) second read: " + secondRead);
            if (firstRead != secondRead) {
                System.out.println("  → Non-repeatable read observed. Same query, same transaction, different result.");
                System.out.println("  → REPEATABLE READ or SERIALIZABLE would have prevented this.");
            }

            reader.commit();
        }
    }

    private static int readBalance(Connection conn) throws Exception {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT balance FROM accounts WHERE id = 1")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
