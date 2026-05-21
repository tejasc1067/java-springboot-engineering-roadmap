import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// By default, each statement is its own transaction. The UPDATE below
// commits immediately — calling rollback() afterwards does nothing.
public class AutoCommitDefault {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE accounts (owner VARCHAR(20), balance INT)");
            stmt.executeUpdate("INSERT INTO accounts VALUES ('Alice', 1000)");

            System.out.println("Autocommit is: " + conn.getAutoCommit());
            System.out.println("Before: " + balance(stmt));

            stmt.executeUpdate("UPDATE accounts SET balance = 500 WHERE owner = 'Alice'");
            System.out.println("After UPDATE (already committed): " + balance(stmt));

            // Try to "undo" — but it's too late, the change has been committed.
            conn.rollback();
            System.out.println("After rollback() attempt: " + balance(stmt));
            System.out.println("  Rollback didn't restore anything because autocommit had already committed.");
        }
    }

    private static int balance(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT balance FROM accounts WHERE owner = 'Alice'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
