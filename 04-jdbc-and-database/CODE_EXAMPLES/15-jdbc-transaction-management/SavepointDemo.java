import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;

// Savepoints let you roll back PART of a transaction without losing the rest.
//
// Example: bulk-import items into an order. If one item fails (out of stock,
// say), undo just that item, keep the others.
//
// Use sparingly — most logic is either all-or-nothing or genuinely separate
// transactions. Savepoints are for "best-effort within a transaction."
public class SavepointDemo {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE order_items (order_id INT, item VARCHAR(50))");

            conn.setAutoCommit(false);

            stmt.executeUpdate("INSERT INTO order_items VALUES (1, 'Laptop')");
            Savepoint afterFirst = conn.setSavepoint("after_first");

            stmt.executeUpdate("INSERT INTO order_items VALUES (1, 'Mouse')");
            Savepoint afterSecond = conn.setSavepoint("after_second");

            try {
                stmt.executeUpdate("INSERT INTO order_items VALUES (1, 'Cable')");
                // Pretend this third item turned out to be out of stock — undo just it.
                throw new RuntimeException("cable out of stock");
            } catch (Exception e) {
                System.out.println("Item 3 failed: " + e.getMessage() + " → rolling back to after_second");
                conn.rollback(afterSecond);   // keep Laptop and Mouse, drop Cable
            }

            conn.commit();
            conn.setAutoCommit(true);

            System.out.println("\nFinal order contents:");
            try (ResultSet rs = stmt.executeQuery("SELECT item FROM order_items")) {
                while (rs.next()) System.out.println("  " + rs.getString(1));
            }
        }
    }
}
