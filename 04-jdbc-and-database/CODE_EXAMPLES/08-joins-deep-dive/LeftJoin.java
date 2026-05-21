import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// LEFT JOIN: all rows from the LEFT table, plus matching rows from the right.
// Customers without orders now appear with NULL amounts.
// Use this when you want "all X, plus their Y if any."
public class LeftJoin {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            String sql = """
                SELECT customers.name AS customer_name, orders.amount
                FROM customers
                LEFT JOIN orders ON customers.id = orders.customer_id
                ORDER BY customers.name, orders.amount
                """;

            System.out.println("LEFT JOIN — Carol and David appear with NULL; orphan order still gone:");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("customer_name");
                    Object amount = rs.getObject("amount");   // getObject so NULL comes through
                    System.out.printf("  %-8s %s%n", name, amount);
                }
            }

            // Common LEFT JOIN pattern: count, treating no-match as zero.
            // Notice COUNT(orders.id) — not COUNT(*) — so NULLs are excluded.
            // If we'd used COUNT(*) here, Carol would get count=1, not 0.
            String countSql = """
                SELECT customers.name, COUNT(orders.id) AS order_count
                FROM customers
                LEFT JOIN orders ON customers.id = orders.customer_id
                GROUP BY customers.name
                ORDER BY customers.name
                """;

            System.out.println("\nOrders per customer (zero counted correctly):");
            try (ResultSet rs = stmt.executeQuery(countSql)) {
                while (rs.next()) {
                    System.out.printf("  %-8s %d%n", rs.getString("name"), rs.getInt("order_count"));
                }
            }
        }
    }

    private static void setup(Statement stmt) throws Exception {
        stmt.executeUpdate("CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50))");
        stmt.executeUpdate("CREATE TABLE orders (id INT PRIMARY KEY, customer_id INT, amount DECIMAL(10, 2))");
        stmt.executeUpdate("INSERT INTO customers VALUES (1,'Alice'),(2,'Bob'),(3,'Carol'),(4,'David')");
        stmt.executeUpdate("INSERT INTO orders VALUES (1,1,100),(2,1,50),(3,2,75),(4,5,200)");
    }
}
