import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

// ResultSet usage in depth:
//   - next() advances the cursor
//   - getXxx by column name (clearer) or by 1-based column index (faster)
//   - ResultSetMetaData reveals what columns came back
public class ResultSetWalkthrough {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE products (id INT, name VARCHAR(50), price DECIMAL(10,2))");
            stmt.executeUpdate("""
                INSERT INTO products VALUES
                    (1, 'Laptop', 1200),
                    (2, 'Mouse',  25),
                    (3, 'Cable',  10)
                """);

            try (ResultSet rs = stmt.executeQuery("SELECT id, name, price FROM products ORDER BY id")) {

                // Metadata: what does the result actually contain?
                ResultSetMetaData md = rs.getMetaData();
                System.out.println("Columns:");
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    System.out.printf("  [%d] %s (%s)%n",
                            i, md.getColumnLabel(i), md.getColumnTypeName(i));
                }

                System.out.println("\nRows:");
                while (rs.next()) {
                    // Access by name — easier to maintain, slower (string lookup).
                    int id = rs.getInt("id");
                    // Access by 1-based index — faster, brittle if column order changes.
                    String name = rs.getString(2);
                    System.out.printf("  id=%d  name=%s  price=%.2f%n",
                            id, name, rs.getBigDecimal("price"));
                }
            }
        }
    }
}
