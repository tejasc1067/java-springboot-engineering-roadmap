import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Production pattern: batched writes inside a transaction.
// All inserts succeed together or none of them do. If any row fails partway
// through, the whole import rolls back.
public class BatchWithTransaction {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {

            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            }

            try {
                bulkInsert(conn, 2000);
                System.out.println("Bulk insert committed. Row count: " + count(conn));
            } catch (Exception e) {
                System.out.println("Bulk insert failed and rolled back: " + e.getMessage());
                System.out.println("Row count after failure: " + count(conn));
            }
        }
    }

    private static void bulkInsert(Connection conn, int rowCount) throws Exception {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, name) VALUES (?, ?)")) {

            for (int i = 1; i <= rowCount; i++) {
                ps.setInt(1, i);
                ps.setString(2, "user_" + i);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static int count(Connection conn) throws Exception {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
