import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// The Friday-afternoon mistake: UPDATE or DELETE without a WHERE clause.
// Run this and look at the affected-row counts. The "bad" statements rewrite
// or wipe every row in the table.
//
// This file is meant to scare you a little. The same SQL shape works against
// a 100M-row production table the same way.
public class DangerousUpdateAndDelete {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(50),
                    role VARCHAR(20)
                )
                """);
            stmt.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'Alice', 'admin'),
                    (2, 'Bob',   'user'),
                    (3, 'Carol', 'user'),
                    (4, 'David', 'user'),
                    (5, 'Eve',   'user')
                """);

            // ---- The disaster: UPDATE without WHERE ----
            // Every user becomes an admin. Not what we wanted.
            int n = stmt.executeUpdate("UPDATE users SET role = 'admin'");
            System.out.println("UPDATE without WHERE — rows affected: " + n);
            // ^ in production this is 5 million users, all suddenly admins.

            // Defensive pattern: preview the WHERE clause as a SELECT first.
            // If the count surprises you, don't run the UPDATE.
            int previewCount = previewCount(stmt,
                    "SELECT COUNT(*) FROM users WHERE name = 'Alice'");
            System.out.println("Preview: WHERE name = 'Alice' would affect " + previewCount + " row(s).");

            // Now we can confidently run the targeted update.
            n = stmt.executeUpdate("UPDATE users SET role = 'admin' WHERE name = 'Alice'");
            System.out.println("Targeted UPDATE affected: " + n);

            // ---- The other disaster: DELETE without WHERE ----
            n = stmt.executeUpdate("DELETE FROM users");
            System.out.println("DELETE without WHERE — rows affected: " + n);
            // The table is now empty.

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                System.out.println("Rows remaining: " + rs.getInt(1));
            }
        }
    }

    private static int previewCount(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
