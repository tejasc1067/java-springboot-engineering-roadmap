import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

// `?` placeholders bind VALUES. They cannot bind identifiers
// (column names, table names, ORDER BY direction).
//
// If the user picks which column to sort by, you can't parameterize it.
// You MUST validate the user's choice against a known allow-list before
// concatenating it into the SQL.
public class DynamicSqlSafely {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    // Allow-list. Only these column names can ever reach the SQL string.
    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of("id", "name", "price");
    private static final Set<String> ALLOWED_DIRECTIONS    = Set.of("ASC", "DESC");

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE products (id INT, name VARCHAR(50), price DECIMAL(10,2))");
                s.executeUpdate("INSERT INTO products VALUES (1,'Laptop',1200),(2,'Mouse',25),(3,'Cable',10)");
            }

            // Honest input — works.
            sortBy(conn, "price", "DESC");

            // Sneaky input — rejected before it reaches the SQL.
            try {
                sortBy(conn, "price; DROP TABLE products; --", "DESC");
            } catch (IllegalArgumentException e) {
                System.out.println("Rejected: " + e.getMessage());
            }

            // Wrong-cased direction also rejected (the allow-list is precise on purpose).
            try {
                sortBy(conn, "name", "RANDOM()");
            } catch (IllegalArgumentException e) {
                System.out.println("Rejected: " + e.getMessage());
            }
        }
    }

    private static void sortBy(Connection conn, String sortColumn, String direction) throws Exception {
        if (!ALLOWED_SORT_COLUMNS.contains(sortColumn)) {
            throw new IllegalArgumentException("invalid sort column: " + sortColumn);
        }
        if (!ALLOWED_DIRECTIONS.contains(direction)) {
            throw new IllegalArgumentException("invalid direction: " + direction);
        }

        // Only safe values reach the SQL — and they're already known constants.
        // Even though we're concatenating, there's no untrusted string here.
        String sql = "SELECT id, name, price FROM products ORDER BY " + sortColumn + " " + direction;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("\nSorted by " + sortColumn + " " + direction + ":");
            while (rs.next()) {
                System.out.printf("  %d  %-10s %.2f%n",
                        rs.getInt("id"), rs.getString("name"), rs.getBigDecimal("price"));
            }
        }
    }
}
