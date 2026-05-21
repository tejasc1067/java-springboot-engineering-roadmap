import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Binding parameters of different types: strings, integers, dates.
// Use the setXxx method that matches the column type — the driver handles
// formatting (and escaping) correctly for each.
public class MultipleParameters {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE orders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        customer_name VARCHAR(50),
                        amount DECIMAL(10, 2),
                        ordered_on DATE
                    )
                    """);
                s.executeUpdate("""
                    INSERT INTO orders (customer_name, amount, ordered_on) VALUES
                        ('Alice', 100.00, '2026-01-05'),
                        ('Alice',  50.00, '2026-01-15'),
                        ('Bob',   200.00, '2026-02-01'),
                        ('Alice',  25.00, '2026-02-20')
                    """);
            }

            // Three placeholders: a string, a number, and a date.
            String sql = """
                SELECT id, amount, ordered_on FROM orders
                WHERE customer_name = ?
                  AND amount > ?
                  AND ordered_on >= ?
                """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "Alice");
                ps.setDouble(2, 30.00);
                ps.setDate(3, Date.valueOf("2026-01-10"));

                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("Alice's orders > $30 placed on or after Jan 10:");
                    while (rs.next()) {
                        System.out.printf("  order #%d  amount=%.2f  on=%s%n",
                                rs.getInt("id"),
                                rs.getBigDecimal("amount"),
                                rs.getDate("ordered_on"));
                    }
                }
            }
        }
    }
}
