import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// HAVING filters groups *after* they're formed. The condition can reference
// aggregate functions, which WHERE cannot (because aggregates don't exist yet
// at the WHERE step).
public class HavingClause {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE products (id INT, category VARCHAR(50), price DECIMAL(10, 2))");
            stmt.executeUpdate("""
                INSERT INTO products VALUES
                    (1, 'electronics', 1200),
                    (2, 'electronics',  800),
                    (3, 'electronics',  150),
                    (4, 'kitchen',       15),
                    (5, 'kitchen',       45),
                    (6, 'books',         25),
                    (7, 'books',         18)
                """);

            // Every category
            print(stmt, "All categories",
                    "SELECT category, COUNT(*) AS n FROM products GROUP BY category");

            // Categories with at least 3 products
            print(stmt, "Categories with >= 3 products",
                    "SELECT category, COUNT(*) AS n FROM products GROUP BY category HAVING COUNT(*) >= 3");

            // Categories whose total revenue exceeds 100
            print(stmt, "Categories with total revenue > 100",
                    "SELECT category, SUM(price) AS total FROM products GROUP BY category HAVING SUM(price) > 100");

            // The next line, if uncommented, would error: WHERE cannot reference COUNT(*).
            //   stmt.executeQuery("SELECT category, COUNT(*) FROM products WHERE COUNT(*) > 1 GROUP BY category");
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
