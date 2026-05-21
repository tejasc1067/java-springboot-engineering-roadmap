import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

// Each index speeds up reads but adds work to every insert/update/delete.
// This demo measures insert time for the same data under three index loads:
//   0 indexes, 1 index, 5 indexes.
//
// On disk-based databases the gap is more pronounced. On in-memory H2 it's
// smaller but still measurable.
public class IndexWriteCost {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        System.out.printf("%-20s %s%n", "indexes", "insert time (ms) for 20,000 rows");
        long t0 = measureInserts("noindex", 0);
        System.out.printf("%-20s %d%n", "0 indexes", t0);
        long t1 = measureInserts("oneindex", 1);
        System.out.printf("%-20s %d  (%.2fx)%n", "1 index", t1, (double) t1 / t0);
        long t5 = measureInserts("fiveindex", 5);
        System.out.printf("%-20s %d  (%.2fx)%n", "5 indexes", t5, (double) t5 / t0);
    }

    private static long measureInserts(String suffix, int indexes) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:perf_" + suffix + ";DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE items (
                    id INT PRIMARY KEY,
                    a VARCHAR(50), b VARCHAR(50), c VARCHAR(50),
                    d VARCHAR(50), e VARCHAR(50)
                )
                """);

            // Add the requested number of indexes BEFORE inserting.
            String[] cols = {"a", "b", "c", "d", "e"};
            for (int i = 0; i < indexes; i++) {
                stmt.executeUpdate("CREATE INDEX idx_" + cols[i] + " ON items(" + cols[i] + ")");
            }

            conn.setAutoCommit(false);

            long start = System.nanoTime();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO items VALUES (?, ?, ?, ?, ?, ?)")) {
                for (int i = 1; i <= 20_000; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "a" + i);
                    ps.setString(3, "b" + i);
                    ps.setString(4, "c" + i);
                    ps.setString(5, "d" + i);
                    ps.setString(6, "e" + i);
                    ps.addBatch();
                    if (i % 1000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return (System.nanoTime() - start) / 1_000_000;
        }
    }
}
