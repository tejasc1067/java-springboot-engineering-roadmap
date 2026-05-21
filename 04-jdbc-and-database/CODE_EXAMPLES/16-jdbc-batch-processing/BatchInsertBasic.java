import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// The basic batch pattern. Queue rows with addBatch(), send them all with
// executeBatch().
public class BatchInsertBasic {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {

            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            }

            String sql = "INSERT INTO users (id, name) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                for (int i = 1; i <= 1000; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "user_" + i);
                    ps.addBatch();        // queue the current bound values
                }

                int[] counts = ps.executeBatch();   // send them all
                System.out.println("Batched inserts: " + counts.length);
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                System.out.println("Rows in users: " + rs.getInt(1));
            }
        }
    }
}
