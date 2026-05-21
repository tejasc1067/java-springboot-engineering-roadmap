import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// All four SQL sublanguages in one program: DDL, DML, DQL, and the DROP at
// the end (also DDL). After running this, the database is back to empty.
public class FullCycle {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            // CREATE
            stmt.executeUpdate("""
                CREATE TABLE products (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10, 2)
                )
                """);
            System.out.println("CREATE → products table exists.");

            // INSERT
            stmt.executeUpdate("INSERT INTO products VALUES (1, 'Laptop', 1200.00)");
            stmt.executeUpdate("INSERT INTO products VALUES (2, 'Mouse', 25.00)");
            stmt.executeUpdate("INSERT INTO products VALUES (3, 'Keyboard', 80.00)");
            printAll(stmt, "After INSERTs");

            // UPDATE
            int updated = stmt.executeUpdate(
                    "UPDATE products SET price = 1100.00 WHERE id = 1");
            System.out.println("UPDATE → rows affected: " + updated);
            printAll(stmt, "After UPDATE");

            // DELETE
            int deleted = stmt.executeUpdate("DELETE FROM products WHERE id = 2");
            System.out.println("DELETE → rows affected: " + deleted);
            printAll(stmt, "After DELETE");

            // DROP
            stmt.executeUpdate("DROP TABLE products");
            System.out.println("DROP → products table no longer exists.");
        }
    }

    private static void printAll(Statement stmt, String label) throws Exception {
        System.out.println("\n--- " + label + " ---");
        try (ResultSet rs = stmt.executeQuery("SELECT * FROM products ORDER BY id")) {
            while (rs.next()) {
                System.out.printf("  %d  %-10s %.2f%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"));
            }
        }
    }
}
