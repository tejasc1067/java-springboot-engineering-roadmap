import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// The "hello world" of JDBC: open a connection, run a trivial query, close.
// If this prints "Connected" and "SELECT 1 returned: 1", JDBC is wired up.
public class ConnectAndPing {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        // try-with-resources: every JDBC type (Connection, Statement, ResultSet)
        // implements AutoCloseable. They're closed automatically — even on
        // exceptions — in REVERSE declaration order.
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            System.out.println("Connected to H2.");

            rs.next();   // advance to the first (and only) row
            System.out.println("SELECT 1 returned: " + rs.getInt(1));
        }
        System.out.println("Connection closed.");
    }
}
