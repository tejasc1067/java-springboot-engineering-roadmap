import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// GROUP BY collapses rows into groups. Aggregate functions (COUNT, SUM, AVG,
// MIN, MAX) describe each group with a single value.
public class GroupByAndAggregates {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE products (
                    id INT PRIMARY KEY,
                    name VARCHAR(50),
                    category VARCHAR(50),
                    price DECIMAL(10, 2)
                )
                """);
            stmt.executeUpdate("""
                INSERT INTO products VALUES
                    (1, 'Laptop',   'electronics', 1200.00),
                    (2, 'Phone',    'electronics',  800.00),
                    (3, 'Headset',  'electronics',  150.00),
                    (4, 'Mug',      'kitchen',       15.00),
                    (5, 'Knife',    'kitchen',       45.00),
                    (6, 'Book',     'books',         25.00),
                    (7, 'Novel',    'books',         18.00)
                """);

            print(stmt, "Number of products per category",
                    "SELECT category, COUNT(*) AS n FROM products GROUP BY category");

            print(stmt, "Total revenue per category",
                    "SELECT category, SUM(price) AS total FROM products GROUP BY category");

            print(stmt, "Average price per category",
                    "SELECT category, AVG(price) AS avg_price FROM products GROUP BY category");

            print(stmt, "Min and max price per category",
                    "SELECT category, MIN(price) AS cheapest, MAX(price) AS priciest FROM products GROUP BY category");

            // GROUP BY rule: every SELECT column must be either grouped or aggregated.
            // The next query would error if uncommented — `name` is neither:
            //   stmt.executeQuery("SELECT category, name, COUNT(*) FROM products GROUP BY category");
        }
    }

    private static void print(Statement stmt, String label, String sql) throws Exception {
        System.out.println("\n--- " + label + " ---");
        try (ResultSet rs = stmt.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) sb.append(rs.getString(i)).append("  ");
                System.out.println(sb);
            }
        }
    }
}
