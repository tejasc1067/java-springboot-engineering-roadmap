import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// FULL OUTER JOIN: every row from both tables, matched where possible.
// Useful for audits ("which orders have no customer AND which customers have no orders").
//
// H2 and PostgreSQL support FULL OUTER JOIN natively. MySQL doesn't — you'd
// have to UNION a LEFT JOIN with a RIGHT JOIN there.
public class FullOuterJoin {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            String sql = """
                SELECT customers.name AS customer_name, orders.amount
                FROM customers
                FULL OUTER JOIN orders ON customers.id = orders.customer_id
                ORDER BY customers.name NULLS LAST, orders.amount NULLS LAST
                """;

            System.out.println("FULL OUTER JOIN — every customer AND every order, matched where possible:");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("customer_name");
                    Object amount = rs.getObject("amount");
                    if (name == null) name = "<NULL>";
                    if (amount == null) amount = "<NULL>";
                    System.out.printf("  %-8s %s%n", name, amount);
                }
            }

            // Diagnostic query: customers with no orders AND orders with no customers.
            System.out.println("\nAudit — anomalies only (one side is NULL):");
            String audit = """
                SELECT customers.name AS customer_name, orders.id AS order_id, orders.amount
                FROM customers
                FULL OUTER JOIN orders ON customers.id = orders.customer_id
                WHERE customers.id IS NULL OR orders.id IS NULL
                """;
            try (ResultSet rs = stmt.executeQuery(audit)) {
                while (rs.next()) {
                    System.out.printf("  customer=%s  order_id=%s  amount=%s%n",
                            rs.getString("customer_name"),
                            rs.getObject("order_id"),
                            rs.getObject("amount"));
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
