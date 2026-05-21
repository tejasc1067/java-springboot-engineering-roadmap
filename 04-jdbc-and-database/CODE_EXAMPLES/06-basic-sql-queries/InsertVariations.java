import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Three ways to write INSERT, ranked from worst to best:
//   1. Without column names — fragile, breaks when schema changes.
//   2. With column names, one row at a time — fine.
//   3. With column names, multiple rows in one statement — fastest.
public class InsertVariations {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE products (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    price DECIMAL(10, 2) DEFAULT 0.00
                )
                """);

            // Way 1: omit columns. Works only if you supply EVERY column in
            // table-definition order. If the schema changes (a column is added
            // or moved), every insert like this silently breaks.
            stmt.executeUpdate("INSERT INTO products VALUES (DEFAULT, 'Laptop', 1200.00)");

            // Way 2: name the columns. The `id` is auto-generated; `price` uses
            // its DEFAULT. This insert keeps working even if columns are added.
            stmt.executeUpdate("INSERT INTO products (name) VALUES ('Mouse')");

            // Way 3: one statement, many rows. Faster than many separate inserts
            // because the database parses and plans the statement only once.
            int batchInserted = stmt.executeUpdate("""
                INSERT INTO products (name, price) VALUES
                    ('Keyboard', 80.00),
                    ('Monitor', 350.00),
                    ('Cable', 10.00)
                """);
            System.out.println("Multi-row INSERT inserted " + batchInserted + " rows.");

            // Verify everything.
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM products ORDER BY id")) {
                System.out.println("\nFinal table contents:");
                while (rs.next()) {
                    System.out.printf("  %d  %-10s %.2f%n",
                            rs.getInt("id"), rs.getString("name"), rs.getBigDecimal("price"));
                }
            }
        }
    }
}
