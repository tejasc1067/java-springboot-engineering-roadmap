import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// The single most common SQL bug: trying to compare to NULL with `=`.
// NULL means "unknown." Two unknowns aren't equal — they're unknown.
// So `x = NULL` is never true. You need `x IS NULL` instead.
//
// Run this and look at the row counts. The bug query returns 0 rows even
// though one user has a NULL age.
public class NullGotcha {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50), age INT)");
            stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 30)");
            stmt.executeUpdate("INSERT INTO users VALUES (2, 'Bob', NULL)");      // age unknown
            stmt.executeUpdate("INSERT INTO users VALUES (3, 'Carol', 25)");

            // WRONG: comparing to NULL with `=` never matches.
            System.out.println("Buggy:  WHERE age = NULL");
            System.out.println("  count = " + count(stmt, "SELECT COUNT(*) FROM users WHERE age = NULL"));

            // RIGHT: use IS NULL.
            System.out.println("Correct: WHERE age IS NULL");
            System.out.println("  count = " + count(stmt, "SELECT COUNT(*) FROM users WHERE age IS NULL"));

            // Similarly: != NULL doesn't work. Use IS NOT NULL.
            System.out.println("Buggy:  WHERE age != NULL");
            System.out.println("  count = " + count(stmt, "SELECT COUNT(*) FROM users WHERE age != NULL"));

            System.out.println("Correct: WHERE age IS NOT NULL");
            System.out.println("  count = " + count(stmt, "SELECT COUNT(*) FROM users WHERE age IS NOT NULL"));
        }
    }

    private static int count(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
