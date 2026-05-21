import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Every DML statement returns the number of rows it affected.
// Checking that number catches a lot of bugs that would otherwise pass silently:
//   - WHERE clause that matches nothing (returns 0)
//   - WHERE clause that matches too much (returns > expected)
//   - Race conditions (the row was just deleted by another process)
public class UpdateWithVerification {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE products (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10, 2),
                    category VARCHAR(50)
                )
                """);
            stmt.executeUpdate("""
                INSERT INTO products VALUES
                    (1, 'Laptop',    1200.00, 'electronics'),
                    (2, 'Phone',      800.00, 'electronics'),
                    (3, 'Mug',         15.00, 'kitchen'),
                    (4, 'Book',        25.00, 'books')
                """);

            // Update one specific row — expect exactly 1 affected.
            int n = stmt.executeUpdate("UPDATE products SET price = 1100.00 WHERE id = 1");
            assertExactly(n, 1, "Lowering Laptop price");

            // Update by category — expect 2 affected (laptop + phone).
            n = stmt.executeUpdate("UPDATE products SET price = price * 0.9 WHERE category = 'electronics'");
            assertExactly(n, 2, "Discount on electronics");

            // Update a row that doesn't exist — expect 0.
            // In real code, this often indicates a bug. Don't ignore it.
            n = stmt.executeUpdate("UPDATE products SET price = 1.00 WHERE id = 9999");
            assertExactly(n, 0, "Updating non-existent row (legitimate 0)");

            // Update using a computed value.
            n = stmt.executeUpdate("UPDATE products SET price = price + 5 WHERE category = 'books'");
            System.out.println("Bumped book prices by $5 — affected: " + n);

            // Final state.
            System.out.println("\nFinal table:");
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM products ORDER BY id")) {
                while (rs.next()) {
                    System.out.printf("  %d %-10s %.2f  %s%n",
                            rs.getInt("id"), rs.getString("name"),
                            rs.getBigDecimal("price"), rs.getString("category"));
                }
            }
        }
    }

    private static void assertExactly(int actual, int expected, String operation) {
        String status = (actual == expected) ? "OK" : "UNEXPECTED";
        System.out.printf("[%s] %s — rows affected: %d (expected %d)%n",
                status, operation, actual, expected);
    }
}
