import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// The same answer written three ways. With small data, all three return the
// same rows. The execution plans differ — at scale, the JOIN usually wins.
//
// Try modifying setup() to insert 100,000 rows and time each query. The
// JOIN's lead grows fast.
public class SubqueryVsJoin {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            setup(stmt);

            String sub  = """
                SELECT id, amount FROM orders
                WHERE customer_id IN (SELECT id FROM customers WHERE country = 'USA')
                """;

            String join = """
                SELECT orders.id, orders.amount
                FROM orders
                INNER JOIN customers ON customers.id = orders.customer_id
                WHERE customers.country = 'USA'
                """;

            String exists = """
                SELECT id, amount FROM orders o
                WHERE EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.country = 'USA')
                """;

            time("Subquery (IN)",   stmt, sub);
            time("INNER JOIN",      stmt, join);
            time("EXISTS",          stmt, exists);
        }
    }

    private static void setup(Statement stmt) throws Exception {
        stmt.executeUpdate("CREATE TABLE customers (id INT, country VARCHAR(20))");
        stmt.executeUpdate("CREATE TABLE orders (id INT, customer_id INT, amount DECIMAL(10,2))");

        for (int i = 1; i <= 50; i++) {
            String country = (i % 3 == 0) ? "USA" : "OTHER";
            stmt.executeUpdate("INSERT INTO customers VALUES (" + i + ",'" + country + "')");
        }
        for (int i = 1; i <= 200; i++) {
            int customer = (i % 50) + 1;
            stmt.executeUpdate("INSERT INTO orders VALUES (" + i + "," + customer + "," + (i * 10) + ")");
        }
    }

    private static void time(String label, Statement stmt, String sql) throws Exception {
        long start = System.nanoTime();
        int count = 0;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) count++;
        }
        long elapsedMicros = (System.nanoTime() - start) / 1_000;
        System.out.printf("%-18s rows=%d  time=%dµs%n", label, count, elapsedMicros);
    }
}
