import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// A naïve login check that builds SQL with string concatenation.
// It "works" for honest input. The next file (02) shows how an attacker
// breaks it. Don't write code like this.
public class StringConcatVulnerable {

    private static final String URL = "jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            setup(conn);

            // Honest user, real credentials.
            boolean ok1 = login(conn, "alice@example.com", "correct-password");
            System.out.println("Honest user login: " + (ok1 ? "ALLOWED" : "DENIED"));

            // Honest user, wrong password.
            boolean ok2 = login(conn, "alice@example.com", "wrong-password");
            System.out.println("Wrong password login: " + (ok2 ? "ALLOWED" : "DENIED"));
        }
    }

    // BAD: builds SQL by concatenating user input directly into the query string.
    private static boolean login(Connection conn, String email, String password) throws Exception {
        String sql = "SELECT id FROM users " +
                "WHERE email = '" + email + "' " +
                "AND password = '" + password + "'";

        // Print the SQL we're about to run, so you can see what the attacker
        // will be able to control in file 02.
        System.out.println("  SQL: " + sql);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static void setup(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, email VARCHAR(100), password VARCHAR(100))");
            s.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'admin@example.com', 'admin-super-secret'),
                    (2, 'alice@example.com', 'correct-password'),
                    (3, 'bob@example.com',   'bobs-password')
                """);
        }
    }
}
