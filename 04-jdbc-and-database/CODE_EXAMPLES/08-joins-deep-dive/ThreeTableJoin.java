import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Joining four tables to produce a single readable report:
//   customers → orders → order_items → products
// This shape (multi-table join) is what most "show me X with all its details"
// queries look like in production.
public class ThreeTableJoin {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            String sql = """
                SELECT
                    customers.name      AS customer,
                    orders.id           AS order_id,
                    products.name       AS product,
                    order_items.quantity,
                    (products.price * order_items.quantity) AS line_total
                FROM customers
                INNER JOIN orders        ON customers.id = orders.customer_id
                INNER JOIN order_items   ON orders.id = order_items.order_id
                INNER JOIN products      ON order_items.product_id = products.id
                ORDER BY customers.name, orders.id, products.name
                """;

            System.out.printf("%-8s %-9s %-10s %-3s %s%n",
                    "customer", "order_id", "product", "qty", "total");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.printf("%-8s %-9d %-10s %-3d %.2f%n",
                            rs.getString("customer"),
                            rs.getInt("order_id"),
                            rs.getString("product"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("line_total"));
                }
            }
        }
    }

    private static void setup(Statement stmt) throws Exception {
        stmt.executeUpdate("CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50))");
        stmt.executeUpdate("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(50), price DECIMAL(10,2))");
        stmt.executeUpdate("CREATE TABLE orders (id INT PRIMARY KEY, customer_id INT)");
        stmt.executeUpdate("""
            CREATE TABLE order_items (
                order_id INT, product_id INT, quantity INT,
                PRIMARY KEY (order_id, product_id)
            )
            """);

        stmt.executeUpdate("INSERT INTO customers VALUES (1,'Alice'),(2,'Bob')");
        stmt.executeUpdate("INSERT INTO products VALUES (10,'Laptop',1200),(11,'Mouse',25),(12,'Cable',10)");
        stmt.executeUpdate("INSERT INTO orders VALUES (100,1),(101,1),(102,2)");
        stmt.executeUpdate("""
            INSERT INTO order_items VALUES
                (100, 10, 1),
                (100, 11, 2),
                (101, 12, 5),
                (102, 11, 1)
            """);
    }
}
