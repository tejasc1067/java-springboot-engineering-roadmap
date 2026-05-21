import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Smoke test: open a connection to H2 in-memory and run the simplest possible
// query, SELECT 1. If this prints "1" you're set up correctly.
public class FirstQuery {

    // jdbc:h2:mem:demo  → in-memory H2 database named "demo"
    // DB_CLOSE_DELAY=-1 → keep it alive for the JVM's lifetime
    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            // ResultSet starts positioned BEFORE the first row.
            // next() advances to the next row and returns false when there are no more.
            while (rs.next()) {
                int value = rs.getInt(1);   // column index is 1-based, not 0-based
                System.out.println("SELECT 1 returned: " + value);
            }
        }

        System.out.println("Connection closed.");
    }
}
