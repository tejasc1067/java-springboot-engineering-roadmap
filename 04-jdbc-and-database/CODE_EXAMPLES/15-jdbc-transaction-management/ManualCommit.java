import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Multi-step transaction, committed on success. Both balances change atomically.
public class ManualCommit {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            setup(conn);
            print(conn, "Before");

            conn.setAutoCommit(false);    // begin transaction
            try (PreparedStatement debit = conn.prepareStatement(
                    "UPDATE accounts SET balance = balance - ? WHERE owner = ?");
                 PreparedStatement credit = conn.prepareStatement(
                    "UPDATE accounts SET balance = balance + ? WHERE owner = ?")) {

                debit.setInt(1, 100);
                debit.setString(2, "Alice");
                debit.executeUpdate();

                credit.setInt(1, 100);
                credit.setString(2, "Bob");
                credit.executeUpdate();

                conn.commit();            // both succeeded → permanent
            } finally {
                conn.setAutoCommit(true); // restore default
            }

            print(conn, "After commit");
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
