import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Atomicity = all-or-nothing. This program shows that until COMMIT, none of
// the changes are visible to the database (or, more precisely, won't survive
// a rollback). After COMMIT, all changes appear together.
public class AtomicityDemo {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE accounts (owner VARCHAR(20) PRIMARY KEY, balance DECIMAL(10,2))");
            stmt.executeUpdate("INSERT INTO accounts VALUES ('Alice', 500), ('Bob', 200)");

            System.out.println("Start:");
            print(stmt);

            // Transaction 1: succeed → COMMIT.
            conn.setAutoCommit(false);
            stmt.executeUpdate("UPDATE accounts SET balance = balance - 100 WHERE owner = 'Alice'");
            stmt.executeUpdate("UPDATE accounts SET balance = balance + 100 WHERE owner = 'Bob'");
            conn.commit();
            System.out.println("\nAfter committed transfer:");
            print(stmt);

            // Transaction 2: change a lot, then ROLLBACK.
            stmt.executeUpdate("UPDATE accounts SET balance = 0 WHERE owner = 'Alice'");
            stmt.executeUpdate("UPDATE accounts SET balance = 999999 WHERE owner = 'Bob'");
            System.out.println("\nMid-transaction (still uncommitted):");
            print(stmt);
            conn.rollback();
            System.out.println("\nAfter rollback (back to previous committed state):");
            print(stmt);

            conn.setAutoCommit(true);
        }
    }

    private static void print(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT owner, balance FROM accounts ORDER BY owner")) {
            while (rs.next()) {
                System.out.printf("  %-6s %.2f%n", rs.getString("owner"), rs.getBigDecimal("balance"));
            }
        }
    }
}
