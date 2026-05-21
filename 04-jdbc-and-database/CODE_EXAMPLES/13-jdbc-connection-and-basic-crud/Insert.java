import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// INSERT with PreparedStatement, and how to read back an auto-generated ID.
//
// Pattern: pass Statement.RETURN_GENERATED_KEYS when preparing the statement,
// then call ps.getGeneratedKeys() after executeUpdate().
public class Insert {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {

            try (Statement s = conn.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE users (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) NOT NULL,
                        email VARCHAR(100)
                    )
                    """);
            }

            // Insert with parameter binding. setString handles escaping correctly,
            // so the apostrophe in "O'Reilly" causes no SQL error.
            String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Alice O'Reilly");
                ps.setString(2, "alice@example.com");
                int rows = ps.executeUpdate();
                System.out.println("Rows inserted: " + rows);

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        System.out.println("Generated id: " + id);
                    }
                }
            }

            // Reuse the same PreparedStatement for another insert.
            // Cheaper than preparing twice — the database can reuse the compiled plan.
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                String[] names = {"Bob", "Carol", "Dave"};
                for (String name : names) {
                    ps.setString(1, name);
                    ps.setString(2, name.toLowerCase() + "@example.com");
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            System.out.println("Inserted " + name + " with id " + keys.getLong(1));
                        }
                    }
                }
            }
        }
    }
}
