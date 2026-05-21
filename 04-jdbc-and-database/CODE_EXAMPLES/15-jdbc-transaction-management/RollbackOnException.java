import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// The canonical multi-step transaction pattern with rollback on failure.
// MEMORIZE THIS SHAPE. You'll write it a hundred times.
//
//   setAutoCommit(false)
//   try {
//       ...work...
//       commit()
//   } catch (X) {
//       rollback()
//       throw X
//   } finally {
//       setAutoCommit(true)
//   }
public class RollbackOnException {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            setup(conn);
            print(conn, "Before");

            try {
                transferWithFailure(conn, "Alice", "Bob", 100);
            } catch (RuntimeException e) {
                System.out.println("\nTransfer failed: " + e.getMessage());
            }

            print(conn, "After failed transfer");
            System.out.println("\nBoth balances unchanged — rollback worked.");
        }
    }

    private static void transferWithFailure(Connection conn, String from, String to, int amount) throws Exception {
        conn.setAutoCommit(false);
        try (PreparedStatement debit = conn.prepareStatement(
                "UPDATE accounts SET balance = balance - ? WHERE owner = ?");
             PreparedStatement credit = conn.prepareStatement(
                "UPDATE accounts SET balance = balance + ? WHERE owner = ?")) {

            debit.setInt(1, amount);
            debit.setString(2, from);
            debit.executeUpdate();

            // Simulate a failure (network glitch, validation, business rule):
            if (true) throw new RuntimeException("simulated failure after debit");

            credit.setInt(1, amount);
            credit.setString(2, to);
            credit.executeUpdate();

            conn.commit();
        } catch (Exception e) {
            conn.rollback();   // crucial: undo the partial work
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void setup(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE accounts (owner VARCHAR(20) PRIMARY KEY, balance INT)");
            s.executeUpdate("INSERT INTO accounts VALUES ('Alice', 500), ('Bob', 200)");
        }
    }

    private static void print(Connection conn, String label) throws Exception {
        System.out.println("\n" + label + ":");
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT owner, balance FROM accounts ORDER BY owner")) {
            while (rs.next()) {
                System.out.printf("  %-6s %d%n", rs.getString(1), rs.getInt(2));
            }
        }
    }
}
