import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

// Insert the same 5000 rows two ways: one executeUpdate per row, then batched.
// On in-memory H2 the gap is smaller than on a real disk database (no network
// latency to amortize). On Postgres/MySQL over a network this same comparison
// usually shows 20-100x speedup for the batched version.
public class BatchVsLoopPerformance {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";
    private static final int N = 5000;

    public static void main(String[] args) throws Exception {

        long loopTime = timeSingleRowInserts();
        long batchTime = timeBatchedInserts();

        System.out.printf("Single-row loop: %d ms (%d rows)%n", loopTime, N);
        System.out.printf("Batched:         %d ms (%d rows)%n", batchTime, N);
        if (batchTime > 0) {
            System.out.printf("Speedup:         %.1fx%n", (double) loopTime / batchTime);
        }
    }

    private static long timeSingleRowInserts() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL.replace("demo", "loop"), "sa", "");
             Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE x (id INT PRIMARY KEY, name VARCHAR(50))");

            conn.setAutoCommit(false);
            long start = System.nanoTime();
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO x VALUES (?, ?)")) {
                for (int i = 1; i <= N; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "u" + i);
                    ps.executeUpdate();      // one round-trip per row
                }
            }
            conn.commit();
            return (System.nanoTime() - start) / 1_000_000;
        }
    }

    private static long timeBatchedInserts() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL.replace("demo", "batch"), "sa", "");
             Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE x (id INT PRIMARY KEY, name VARCHAR(50))");

            conn.setAutoCommit(false);
            long start = System.nanoTime();
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO x VALUES (?, ?)")) {
                for (int i = 1; i <= N; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "u" + i);
                    ps.addBatch();
                }
                ps.executeBatch();           // one round-trip total
            }
            conn.commit();
            return (System.nanoTime() - start) / 1_000_000;
        }
    }
}
