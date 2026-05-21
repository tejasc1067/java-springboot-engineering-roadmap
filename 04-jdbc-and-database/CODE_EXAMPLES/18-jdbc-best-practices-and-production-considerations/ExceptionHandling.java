import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

// Inspect SQLException properly: state, code, chained exceptions.
// Deliberately trigger a unique-constraint violation to see what comes back.
public class ExceptionHandling {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, email VARCHAR(100) UNIQUE)");
                s.executeUpdate("INSERT INTO users VALUES (1, 'alice@example.com')");
            }

            // Try to insert a duplicate id (PK violation).
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, email) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "bob@example.com");
                ps.executeUpdate();
            } catch (SQLException e) {
                describe("Primary key violation", e);
            }

            // Try to insert a duplicate email (UNIQUE violation).
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, email) VALUES (?, ?)")) {
                ps.setInt(1, 2);
                ps.setString(2, "alice@example.com");
                ps.executeUpdate();
            } catch (SQLException e) {
                describe("Unique constraint violation", e);
            }

            // Try invalid SQL (syntax error).
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("INSERT INTO users (id, name) VALUES (3, 'Carol')");  // wrong column name
            } catch (SQLException e) {
                describe("Schema error (wrong column)", e);
            }
        }
    }

    private static void describe(String label, SQLException e) {
        System.out.println("\n--- " + label + " ---");
        System.out.println("  message:   " + e.getMessage());
        System.out.println("  SQLState:  " + e.getSQLState());
        System.out.println("  errorCode: " + e.getErrorCode());

        // Some failures (batches especially) chain exceptions.
        SQLException next = e.getNextException();
        while (next != null) {
            System.out.println("  chained:   " + next.getMessage());
            next = next.getNextException();
        }
    }
}
