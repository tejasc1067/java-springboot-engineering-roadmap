import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// INNER JOIN: only rows where the condition matches BOTH sides.
// Customers without orders disappear. Orphan orders disappear.
public class InnerJoin {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            // Note `customers.name AS customer_name` — both tables have an `id`
            // column, and we'd hit ambiguity errors if we tried to SELECT just `id`.
            // The qualifier `customers.name` says exactly which table's column.
            String sql = """
                SELECT customers.name AS customer_name, orders.amount
                FROM customers
                INNER JOIN orders ON customers.id = orders.customer_id
                ORDER BY customers.name, orders.amount
                """;

            System.out.println("INNER JOIN — Carol, David, and the orphan order all missing:");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.printf("  %-8s %.2f%n",
                            rs.getString("customer_name"),
                            rs.getBigDecimal("amount"));
                }
            }
        }
    }

    private static void setup(Statement stmt) throws Exception {
        stmt.executeUpdate("CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50))");
        stmt.executeUpdate("CREATE TABLE orders (id INT PRIMARY KEY, customer_id INT, amount DECIMAL(10, 2))");

        stmt.executeUpdate("""
            INSERT INTO customers VALUES
                (1, 'Alice'), (2, 'Bob'), (3, 'Carol'), (4, 'David')
            """);
        stmt.executeUpdate("""
            INSERT INTO orders VALUES
                (1, 1, 100.00),
                (2, 1,  50.00),
                (3, 2,  75.00),
                (4, 5, 200.00)
            """);
        // Order #4 references customer 5, who doesn't exist — an orphan.
        // No FK constraint here so the insert succeeds. See topic 03.
    }
}
