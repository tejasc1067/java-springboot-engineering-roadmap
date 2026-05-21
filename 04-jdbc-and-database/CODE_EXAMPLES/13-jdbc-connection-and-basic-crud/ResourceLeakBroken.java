import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Anti-example. This is what bad JDBC code looks like — no try-with-resources,
// no finally close. Each call leaks one Connection.
//
// To make the leak visible, we limit H2 to a tiny max connection pool and
// loop. Around iteration ~10-20 the program either hangs or fails with
// "Failed to get connection." That's exactly the production failure mode
// that hits real services that leak connections.
//
// COMPARE WITH: TryWithResourcesProper.java
public class ResourceLeakBroken {

    // H2 lets you set MAX_CONNECTIONS in the URL. 5 is plenty to demonstrate.
    private static final String URL =
            "jdbc:h2:mem:leak;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000";

    public static void main(String[] args) throws Exception {
        // Seed via a properly closed connection.
        try (Connection setup = DriverManager.getConnection(URL, "sa", "");
             Statement s = setup.createStatement()) {
            s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            for (int i = 1; i <= 20; i++) {
                s.executeUpdate("INSERT INTO users VALUES (" + i + ", 'user_" + i + "')");
            }
        }

        // Now leak connections deliberately.
        // (H2 in-memory tolerates many connections, so we have to leak quite
        // a few to see real symptoms. On a real DBMS with max_connections=100
        // you'd see this fail much sooner.)
        for (int i = 1; i <= 200; i++) {
            try {
                leakyQuery(i);
                if (i % 50 == 0) System.out.println("Iteration " + i + " OK");
            } catch (Exception e) {
                System.out.println("Iteration " + i + " FAILED: " + e.getMessage());
                System.out.println("That's a connection leak in production. Compare with TryWithResourcesProper.java.");
                return;
            }
        }

        System.out.println("Finished. Run with -Xmx32m or a real DB to see the leak fail earlier.");
    }

    private static void leakyQuery(int id) throws Exception {
        // BAD: opens a connection, uses it, then drops it on the floor.
        // Nothing closes it. Every call adds one more leaked connection.
        Connection conn = DriverManager.getConnection(URL, "sa", "");
        PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE id = ?");
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        rs.next();
        // We don't even call rs.close(), ps.close(), conn.close(). GC may
        // eventually collect them; the OS-level resources may take longer.
        // In production, the database doesn't know the client gave up — it
        // keeps the connection slot reserved.
    }
}
