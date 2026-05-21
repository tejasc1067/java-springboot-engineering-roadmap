import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Same SELECT written two ways. Both work, but PreparedStatement should be
// your default any time the query has values that aren't hardcoded constants.
//
// Reasons to prefer PreparedStatement:
//   1. Safety — it prevents SQL injection (topic 14). String concatenation doesn't.
//   2. Performance — the database can cache the compiled query plan, so running
//      the same query many times with different parameters is faster.
//   3. Type correctness — setString, setInt, setTimestamp handle quoting/escaping
//      for you, including edge cases like apostrophes in names.
//
// Use plain Statement only for DDL (CREATE TABLE) or completely fixed queries
// with no parameters.
public class StatementVsPreparedStatement {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {

            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("CREATE TABLE users (id INT, name VARCHAR(50))");
                setup.executeUpdate("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
            }

            String wantName = "Alice";

            // Approach 1 — Statement with string concatenation. Don't do this with user input.
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT id FROM users WHERE name = '" + wantName + "'")) {
                while (rs.next()) {
                    System.out.println("[Statement] found id=" + rs.getInt("id"));
                }
            }

            // Approach 2 — PreparedStatement with ? placeholders. Always do this with values.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM users WHERE name = ?")) {
                ps.setString(1, wantName);   // 1-based index
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("[PreparedStatement] found id=" + rs.getInt("id"));
                    }
                }
            }

            // What happens with a name that contains an apostrophe?
            // Approach 1 breaks (syntax error or SQL injection).
            // Approach 2 handles it correctly.
            String trickyName = "O'Reilly";
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users VALUES (?, ?)")) {
                ps.setInt(1, 3);
                ps.setString(2, trickyName);   // no escaping needed
                ps.executeUpdate();
            }
            System.out.println("Inserted user with apostrophe in name: " + trickyName);
        }
    }
}
