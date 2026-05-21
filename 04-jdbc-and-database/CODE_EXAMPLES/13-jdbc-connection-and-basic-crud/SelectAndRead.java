import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// SELECT with a parameter, mapping each row into a Java record.
// This shape — "method that returns a List<DomainObject>" — is the building
// block of every Data Access Object (DAO).
public class SelectAndRead {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    record User(long id, String name, String email, String country) {}

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            seed(conn);

            List<User> usaUsers = findByCountry(conn, "USA");
            System.out.println("Found " + usaUsers.size() + " USA users:");
            usaUsers.forEach(u -> System.out.println("  " + u));
        }
    }

    private static List<User> findByCountry(Connection conn, String country) throws Exception {
        String sql = "SELECT id, name, email, country FROM users WHERE country = ? ORDER BY name";
        List<User> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, country);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new User(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("country")));
                }
            }
        }
        return result;
    }

    private static void seed(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(50),
                    email VARCHAR(100),
                    country VARCHAR(20)
                )
                """);
            s.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'Alice', 'alice@example.com', 'USA'),
                    (2, 'Bob',   'bob@example.com',   'USA'),
                    (3, 'Carol', 'carol@example.com', 'India'),
                    (4, 'Dave',  'dave@example.com',  'UK')
                """);
        }
    }
}
