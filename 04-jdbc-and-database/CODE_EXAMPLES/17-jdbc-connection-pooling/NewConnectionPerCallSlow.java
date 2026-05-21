import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Naïve approach: open a new connection for every query.
// On in-memory H2 this is cheap; over a real network it would be much slower.
// Compare with HikariCpBasic.java.
public class NewConnectionPerCallSlow {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        // Seed once with a normal connection.
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            for (int i = 1; i <= 100; i++) {
                s.executeUpdate("INSERT INTO users VALUES (" + i + ", 'u" + i + "')");
            }
        }

        long start = System.nanoTime();

        for (int i = 1; i <= 100; i++) {
            // Open a fresh connection for every query — this is the wasteful part.
            try (Connection conn = DriverManager.getConnection(URL, "sa", "");
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE id = ?")) {
                ps.setInt(1, i);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("100 queries, fresh connection each time: " + elapsedMs + " ms");
        System.out.println("(On a real network database this would be ~100x slower.)");
    }
}
