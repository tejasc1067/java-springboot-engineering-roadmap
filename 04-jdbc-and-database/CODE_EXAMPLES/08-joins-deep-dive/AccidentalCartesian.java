import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// What happens if you forget the ON clause: a Cartesian product.
// Every row from the left × every row from the right. With small tables it's
// just funny. With production-scale tables (100k × 100k) it's a 10-billion-row
// disaster that grinds the server to a halt.
//
// Watch the row counts grow.
public class AccidentalCartesian {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            System.out.println("customers: " + count(stmt, "SELECT COUNT(*) FROM customers"));
            System.out.println("orders:    " + count(stmt, "SELECT COUNT(*) FROM orders"));

            // Correct INNER JOIN — only matching pairs.
            int joinedRows = count(stmt, """
                SELECT COUNT(*) FROM customers
                INNER JOIN orders ON customers.id = orders.customer_id
                """);
            System.out.println("\nINNER JOIN ON customer_id → rows: " + joinedRows);

            // Buggy: no ON clause (CROSS JOIN). 4 customers × 4 orders = 16 rows.
            int crossRows = count(stmt, "SELECT COUNT(*) FROM customers CROSS JOIN orders");
            System.out.println("CROSS JOIN (no ON)        → rows: " + crossRows);

            // Worse: the legacy comma syntax produces the same Cartesian.
            // Modern SQL convention is to never use this form, exactly to
            // make accidents like this impossible.
            int commaRows = count(stmt, "SELECT COUNT(*) FROM customers, orders");
            System.out.println("FROM customers, orders    → rows: " + commaRows);

            // Lesson: always write JOIN ... ON, never use comma-separated FROM.
            // The verbose form makes a missing ON a parse error in most databases.
        }
    }

    private static void setup(Statement stmt) throws Exception {
        stmt.executeUpdate("CREATE TABLE customers (id INT, name VARCHAR(20))");
        stmt.executeUpdate("CREATE TABLE orders (id INT, customer_id INT, amount DECIMAL(10, 2))");
        stmt.executeUpdate("INSERT INTO customers VALUES (1,'Alice'),(2,'Bob'),(3,'Carol'),(4,'David')");
        stmt.executeUpdate("INSERT INTO orders VALUES (1,1,100),(2,1,50),(3,2,75),(4,5,200)");
    }

    private static int count(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
