import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Walks through the full CRUD lifecycle for a single product, asserting at
// each step that the database is in the expected state.
public class FullCrudCycle {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE products (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10, 2)
                )
                """);

            // CREATE (INSERT)
            int created = stmt.executeUpdate(
                    "INSERT INTO products (id, name, price) VALUES (1, 'Laptop', 1200.00)");
            check(created == 1, "Insert should affect 1 row");
            check(rowCount(stmt) == 1, "Table should have 1 row");

            // READ (SELECT)
            try (ResultSet rs = stmt.executeQuery("SELECT name, price FROM products WHERE id = 1")) {
                rs.next();
                check("Laptop".equals(rs.getString("name")), "Name should be Laptop");
                check(rs.getBigDecimal("price").doubleValue() == 1200.00, "Price should be 1200.00");
            }

            // UPDATE
            int updated = stmt.executeUpdate("UPDATE products SET price = 1100.00 WHERE id = 1");
            check(updated == 1, "Update should affect 1 row");
            try (ResultSet rs = stmt.executeQuery("SELECT price FROM products WHERE id = 1")) {
                rs.next();
                check(rs.getBigDecimal("price").doubleValue() == 1100.00, "Price should be 1100.00 after update");
            }

            // DELETE
            int deleted = stmt.executeUpdate("DELETE FROM products WHERE id = 1");
            check(deleted == 1, "Delete should affect 1 row");
            check(rowCount(stmt) == 0, "Table should be empty");

            System.out.println("\nFull CRUD cycle passed.");
        }
    }

    private static int rowCount(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void check(boolean condition, String description) {
        String marker = condition ? "PASS" : "FAIL";
        System.out.printf("[%s] %s%n", marker, description);
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
