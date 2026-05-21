import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Callable;

// Wraps the four-step transaction boilerplate in a reusable helper.
// This is essentially what Spring's @Transactional does behind the scenes.
public class TransactionHelper {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            setup(conn);

            // Successful transfer.
            Integer rowsAffected = inTransaction(conn, () -> {
                update(conn, "Alice", -100);
                update(conn, "Bob",   +100);
                return 2;
            });
            System.out.println("Successful transfer affected rows: " + rowsAffected);
            print(conn, "After commit");

            // Failed transfer — rolls back automatically.
            try {
                inTransaction(conn, () -> {
                    update(conn, "Alice", -50);
                    throw new RuntimeException("simulated failure");
                });
            } catch (Exception e) {
                System.out.println("\nCaught: " + e.getMessage());
            }
            print(conn, "After rollback");
        }
    }

    // The reusable helper. Every multi-step write goes through this.
    private static <T> T inTransaction(Connection conn, Callable<T> work) throws Exception {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            T result = work.call();
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static void update(Connection conn, String owner, int delta) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE accounts SET balance = balance + ? WHERE owner = ?")) {
            ps.setInt(1, delta);
            ps.setString(2, owner);
            ps.executeUpdate();
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
