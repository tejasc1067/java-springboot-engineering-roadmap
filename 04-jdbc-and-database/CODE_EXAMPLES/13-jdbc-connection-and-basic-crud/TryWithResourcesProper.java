import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// A DAO-style class showing the full proper resource-management pattern for
// every CRUD operation. Notice:
//   - Every Connection, Statement, PreparedStatement, ResultSet is in a
//     try-with-resources.
//   - Resources are declared in the order they need to be created, and they
//     close in reverse order automatically.
//   - The Connection comes from a single helper so changing it (e.g. to a
//     pooled DataSource later) is one edit.
public class TryWithResourcesProper {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    record Product(long id, String name, double price) {}

    public static void main(String[] args) throws Exception {
        bootstrapSchema();

        long id1 = create(new Product(0, "Laptop", 1200));
        long id2 = create(new Product(0, "Mouse", 25));

        System.out.println("After create: " + readAll());

        update(id1, 1100);
        System.out.println("After update: " + readAll());

        delete(id2);
        System.out.println("After delete: " + readAll());
    }

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection(URL, "sa", "");
    }

    private static void bootstrapSchema() throws Exception {
        try (Connection conn = openConnection();
             Statement s = conn.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE products (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    price DECIMAL(10, 2)
                )
                """);
        }
    }

    private static long create(Product p) throws Exception {
        String sql = "INSERT INTO products (name, price) VALUES (?, ?)";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.name());
            ps.setDouble(2, p.price());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static List<Product> readAll() throws Exception {
        List<Product> result = new ArrayList<>();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, price FROM products ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Product(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getDouble("price")));
            }
        }
        return result;
    }

    private static int update(long id, double newPrice) throws Exception {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE products SET price = ? WHERE id = ?")) {
            ps.setDouble(1, newPrice);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    private static int delete(long id) throws Exception {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM products WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}
