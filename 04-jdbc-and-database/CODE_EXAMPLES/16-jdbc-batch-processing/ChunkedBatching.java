import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// For very large imports (millions of rows), one giant addBatch loop buffers
// everything in memory. Instead, flush every CHUNK_SIZE rows.
//
// Adjusting CHUNK_SIZE:
//   - Too small (e.g. 10): minimal benefit over no batching at all.
//   - Too large (e.g. 1,000,000): high memory use; risk of OOM.
//   - Common sweet spot: 500 to 5000 depending on row size.
public class ChunkedBatching {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";
    private static final int TOTAL = 50_000;
    private static final int CHUNK_SIZE = 1000;

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE rows (id INT PRIMARY KEY, value VARCHAR(50))");
            }

            conn.setAutoCommit(false);
            long start = System.nanoTime();
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO rows VALUES (?, ?)")) {
                int inBatch = 0;
                for (int i = 1; i <= TOTAL; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "v" + i);
                    ps.addBatch();
                    inBatch++;

                    if (inBatch == CHUNK_SIZE) {
                        ps.executeBatch();
                        inBatch = 0;
                    }
                }
                // Flush whatever didn't fill the last chunk.
                if (inBatch > 0) {
                    ps.executeBatch();
                }
            }
            conn.commit();
            conn.setAutoCommit(true);

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("Inserted %d rows in chunks of %d → %d ms%n",
                    TOTAL, CHUNK_SIZE, elapsedMs);

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM rows")) {
                rs.next();
                System.out.println("Row count: " + rs.getInt(1));
            }
        }
    }
}
