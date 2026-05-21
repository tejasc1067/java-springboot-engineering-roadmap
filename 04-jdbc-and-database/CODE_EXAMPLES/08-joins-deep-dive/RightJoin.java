import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// RIGHT JOIN: all rows from the RIGHT table, plus matches from the left.
// Mirror image of LEFT JOIN. Rarely used in practice — you can always rewrite
// a RIGHT JOIN as a LEFT JOIN by swapping the table order.
public class RightJoin {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            String sql = """
                SELECT customers.name AS customer_name, orders.amount
                FROM customers
                RIGHT JOIN orders ON customers.id = orders.customer_id
                ORDER BY customers.name NULLS LAST, orders.amount
                """;

            System.out.println("RIGHT JOIN — orphan order appears with NULL name; Carol/David gone:");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("customer_name");
                    if (name == null) name = "<NULL>";   // make NULL visible in output
                    System.out.printf("  %-8s %.2f%n", name, rs.getBigDecimal("amount"));
                }
            }

            // Equivalent query rewritten as a LEFT JOIN, no RIGHT JOIN needed.
            // Notice we swapped FROM orders / LEFT JOIN customers.
            System.out.println("\nSame result, written as a LEFT JOIN with tables swapped:");
            String leftEquivalent = """
                SELECT customers.name AS customer_name, orders.amount
                FROM orders
                LEFT JOIN customers ON customers.id = orders.customer_id
                ORDER BY customers.name NULLS LAST, orders.amount
                """;
            try (ResultSet rs = stmt.executeQuery(leftEquivalent)) {
                while (rs.next()) {
                    String name = rs.getString("customer_name");
                    if (name == null) name = "<NULL>";
                    System.out.printf("  %-8s %.2f%n", name, rs.getBigDecimal("amount"));
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
