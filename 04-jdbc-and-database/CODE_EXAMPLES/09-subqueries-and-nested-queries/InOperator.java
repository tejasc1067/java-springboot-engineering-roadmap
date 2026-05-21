import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Subquery returns a set of values; outer query checks membership with IN.
public class InOperator {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE customers (id INT, name VARCHAR(50), country VARCHAR(20))");
            stmt.executeUpdate("CREATE TABLE orders (id INT, customer_id INT, amount DECIMAL(10,2))");

            stmt.executeUpdate("INSERT INTO customers VALUES (1,'Alice','USA'),(2,'Bob','India'),(3,'Carol','USA'),(4,'Dan','UK')");
            stmt.executeUpdate("INSERT INTO orders VALUES (1,1,100),(2,2,50),(3,3,75),(4,4,200)");

            // Orders placed by USA customers.
            String sql = """
                SELECT id, customer_id, amount FROM orders
                WHERE customer_id IN (SELECT id FROM customers WHERE country = 'USA')
                """;

            try (ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Orders from USA customers:");
                while (rs.next()) {
                    System.out.printf("  order #%d  customer=%d  amount=%.2f%n",
                            rs.getInt("id"), rs.getInt("customer_id"), rs.getBigDecimal("amount"));
                }
            }

            // NOT IN variant: customers who have placed NO orders.
            // Be careful: if the subquery returns NULL, NOT IN behaves weirdly.
            // EXISTS is safer in that case — see ExistsOperator.java.
            String notIn = """
                SELECT name FROM customers
                WHERE id NOT IN (SELECT customer_id FROM orders)
                """;
            try (ResultSet rs = stmt.executeQuery(notIn)) {
                System.out.println("\nCustomers with no orders:");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("name"));
                }
                // (none in this dataset — every customer has an order)
            }
        }
    }
}
