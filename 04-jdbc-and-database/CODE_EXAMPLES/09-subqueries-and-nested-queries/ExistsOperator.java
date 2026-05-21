import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// EXISTS asks "does the subquery return at least one row?"
// Two reasons to prefer EXISTS over IN:
//   1. It short-circuits — finds one match and stops, faster on big data.
//   2. It handles NULL correctly. `WHERE x IN (1, NULL)` may not behave
//      the way you'd expect; EXISTS has no such pitfall.
public class ExistsOperator {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE customers (id INT, name VARCHAR(50))");
            stmt.executeUpdate("CREATE TABLE orders (id INT, customer_id INT, amount DECIMAL(10,2))");
            stmt.executeUpdate("INSERT INTO customers VALUES (1,'Alice'),(2,'Bob'),(3,'Carol'),(4,'Dan')");
            stmt.executeUpdate("INSERT INTO orders VALUES (1,1,100),(2,2,50)");

            // Customers who have placed at least one order.
            // The inner SELECT 1 is conventional — only the *existence* matters,
            // not the value, so we don't bother projecting real columns.
            String sql = """
                SELECT name FROM customers c
                WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id)
                """;

            try (ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Customers with orders:");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("name"));
                }
            }

            // NOT EXISTS — customers without any orders.
            String notExists = """
                SELECT name FROM customers c
                WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id)
                """;
            try (ResultSet rs = stmt.executeQuery(notExists)) {
                System.out.println("\nCustomers without orders:");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("name"));
                }
            }
        }
    }
}
