import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Correlated subquery: inner query references the outer query, so the database
// re-evaluates it once for every outer row.
//
// Works fine on tiny data. Catastrophic on large tables — N customers means
// N executions of the inner count. The cure is almost always to rewrite as
// a JOIN + GROUP BY (see SubqueryVsJoin.java).
public class CorrelatedSubquery {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE customers (id INT, name VARCHAR(50))");
            stmt.executeUpdate("CREATE TABLE orders (id INT, customer_id INT, amount DECIMAL(10,2))");
            stmt.executeUpdate("INSERT INTO customers VALUES (1,'Alice'),(2,'Bob'),(3,'Carol')");
            stmt.executeUpdate("INSERT INTO orders VALUES (1,1,100),(2,1,50),(3,2,200)");

            // For each customer, count their orders.
            // The inner (SELECT COUNT...) references c.id from the outer query.
            // The database evaluates the inner SELECT once per row of `c`.
            String sql = """
                SELECT name,
                       (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id) AS order_count
                FROM customers c
                ORDER BY name
                """;

            try (ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Order counts via correlated subquery:");
                while (rs.next()) {
                    System.out.printf("  %-8s %d%n",
                            rs.getString("name"), rs.getInt("order_count"));
                }
            }

            // Same answer, far more scalable, written as a LEFT JOIN + GROUP BY.
            // The database evaluates this in essentially one pass.
            String joinVersion = """
                SELECT c.name, COUNT(o.id) AS order_count
                FROM customers c
                LEFT JOIN orders o ON o.customer_id = c.id
                GROUP BY c.name
                ORDER BY c.name
                """;

            try (ResultSet rs = stmt.executeQuery(joinVersion)) {
                System.out.println("\nSame result via LEFT JOIN + GROUP BY:");
                while (rs.next()) {
                    System.out.printf("  %-8s %d%n",
                            rs.getString("name"), rs.getInt("order_count"));
                }
            }
        }
    }
}
