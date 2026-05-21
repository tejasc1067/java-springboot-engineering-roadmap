import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// The fix. Same login function rewritten with PreparedStatement.
// We feed it the EXACT same attacker input from file 02. The bypass no longer
// works — the attacker's string is treated as a literal value to match, not
// as SQL.
public class PreparedStatementSafe {

    private static final String URL = "jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            setup(conn);

            // 1. Honest user still works.
            int honest = login(conn, "alice@example.com", "correct-password");
            System.out.println("Honest user: " + (honest != -1 ? "ALLOWED as id=" + honest : "DENIED"));

            // 2. Wrong password still rejected.
            int wrong = login(conn, "alice@example.com", "wrong");
            System.out.println("Wrong password: " + (wrong != -1 ? "ALLOWED" : "DENIED"));

            // 3. Attacker's bypass — the same payload that worked in 02.
            String attackerEmail = "anything' OR '1'='1' --";
            int attacker = login(conn, attackerEmail, "anything");
            System.out.println("Attacker bypass attempt: " + (attacker != -1 ? "ALLOWED" : "DENIED"));
            System.out.println("  ^ DENIED, because the email column literally doesn't contain that string.");
        }
    }

    // SAFE: SQL template with ? placeholders, parameters bound separately.
    private static int login(Connection conn, String email, String password) throws Exception {
        String sql = "SELECT id FROM users WHERE email = ? AND password = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        }
    }

    private static void setup(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, email VARCHAR(100), password VARCHAR(100))");
            s.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'admin@example.com', 'admin-super-secret'),
                    (2, 'alice@example.com', 'correct-password')
                """);
        }
    }
}
