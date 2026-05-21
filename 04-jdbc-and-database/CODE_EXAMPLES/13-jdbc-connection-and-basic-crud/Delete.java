import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

// DELETE with PreparedStatement. Same shape and verification discipline as UPDATE.
public class Delete {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            seed(conn);
            System.out.println("Initial row count: " + count(conn));

            // Delete one user by id.
            int deleted = deleteById(conn, 1L);
            System.out.println("Deleted id=1 → rows affected: " + deleted);
            System.out.println("Row count after: " + count(conn));

            // Delete a row that doesn't exist. Returns 0 — possibly a bug.
            int missing = deleteById(conn, 9999L);
            System.out.println("Deleted id=9999 → rows affected: " + missing);
            if (missing == 0) {
                System.out.println("  (id 9999 didn't exist — caller probably had stale state)");
            }
        }
    }

    private static int deleteById(Connection conn, long userId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            return ps.executeUpdate();
        }
    }

    private static int count(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users");
             java.sql.ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void seed(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(50))");
            s.executeUpdate("INSERT INTO users VALUES (1,'Alice'),(2,'Bob'),(3,'Carol')");
        }
    }
}
