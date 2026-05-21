import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// The simplest subquery: an aggregate value used in an outer WHERE clause.
// Inner query runs once. Outer query uses the result.
public class SimpleSubquery {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE users (id INT, name VARCHAR(50), age INT)");
            stmt.executeUpdate("""
                INSERT INTO users VALUES
                    (1,'Alice',30),(2,'Bob',22),(3,'Carol',45),
                    (4,'Dan',18),(5,'Eve',35)
                """);

            // First, show the average separately so the subquery makes sense.
            try (ResultSet rs = stmt.executeQuery("SELECT AVG(age) FROM users")) {
                rs.next();
                System.out.println("Average age: " + rs.getBigDecimal(1));
            }

            // Subquery: users older than the average.
            // The inner query runs once, returns a single number, the outer
            // query uses it as if it were a literal.
            String sql = """
                SELECT name, age FROM users
                WHERE age > (SELECT AVG(age) FROM users)
                ORDER BY age DESC
                """;
            System.out.println("\nUsers above the average:");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.printf("  %-8s %d%n", rs.getString("name"), rs.getInt("age"));
                }
            }
        }
    }
}
