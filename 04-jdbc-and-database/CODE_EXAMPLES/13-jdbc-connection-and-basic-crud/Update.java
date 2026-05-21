import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

// UPDATE with PreparedStatement. Always check the returned row count.
public class Update {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            seed(conn);

            // 1. Update the email of a known user.
            int updated = updateEmail(conn, 1L, "new-alice@example.com");
            System.out.println("Update user 1 → rows affected: " + updated);
            // Expect: 1

            // 2. Update a non-existent user. Row count = 0.
            //    Usually a bug in the caller — they thought the row existed.
            int missing = updateEmail(conn, 9999L, "nobody@example.com");
            System.out.println("Update user 9999 → rows affected: " + missing);
            if (missing == 0) {
                System.out.println("  (no user 9999 — probably a bug if we expected one)");
            }
        }
    }

    private static int updateEmail(Connection conn, long userId, String newEmail) throws Exception {
        String sql = "UPDATE users SET email = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newEmail);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    private static void seed(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(50), email VARCHAR(100))");
            s.executeUpdate("INSERT INTO users VALUES (1,'Alice','alice@example.com'),(2,'Bob','bob@example.com')");
        }
    }
}
