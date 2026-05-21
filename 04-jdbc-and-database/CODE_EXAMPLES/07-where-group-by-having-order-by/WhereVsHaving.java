import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// The same dataset, three queries, showing exactly when WHERE belongs and when
// HAVING belongs.
//
// Rule: row-level condition → WHERE.  Group-level (aggregate) condition → HAVING.
public class WhereVsHaving {

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
                    (7, 'books',         18),
                    (8, 'books',          5)
                """);

            // 1. WHERE before grouping: consider only products priced > 100,
            //    then count how many remain in each category.
            //    "What's the count of expensive products per category?"
            print(stmt, "WHERE only",
                    "SELECT category, COUNT(*) AS n FROM products WHERE price > 100 GROUP BY category");

            // 2. HAVING after grouping: count every product, then show only
            //    categories with more than 2 products.
            //    "Which categories have lots of products?"
            print(stmt, "HAVING only",
                    "SELECT category, COUNT(*) AS n FROM products GROUP BY category HAVING COUNT(*) > 2");

            // 3. Both: only consider products priced > 10 in the count,
            //    then show only categories where that filtered count > 2.
            //    "Which categories have more than 2 non-trivial products?"
            print(stmt, "WHERE and HAVING",
                    """
                    SELECT category, COUNT(*) AS n
                    FROM products
                    WHERE price > 10
                    GROUP BY category
                    HAVING COUNT(*) > 2
                    """);

            // Performance tip: when a condition CAN go in WHERE, put it there.
            // WHERE filters before grouping → fewer rows to group → faster.
            // The next two queries return the same result, but the first is faster:
            //   WHERE category = 'electronics' GROUP BY category   (filters first)
            //   GROUP BY category HAVING category = 'electronics'  (groups all, discards most)
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
