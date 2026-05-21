import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// ORDER BY: ascending (default), descending, multi-column.
// Without ORDER BY the database is free to return rows in any order — don't
// rely on insertion order even if it usually works.
public class OrderBy {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE users (id INT, name VARCHAR(50), age INT, country VARCHAR(20))");
            stmt.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'Alice', 30, 'USA'),
                    (2, 'Bob',   25, 'USA'),
                    (3, 'Carol', 45, 'India'),
                    (4, 'David', 22, 'India'),
                    (5, 'Eve',   28, 'UK')
                """);

            print(stmt, "Ascending age (default)",
                    "SELECT name, age FROM users ORDER BY age");

            print(stmt, "Descending age (oldest first)",
                    "SELECT name, age FROM users ORDER BY age DESC");

            // Multi-column: by country first, then by age within each country.
            // Useful for grouped listings.
            print(stmt, "Country, then age within country",
                    "SELECT country, name, age FROM users ORDER BY country, age");

            // Mixed direction per column.
            print(stmt, "Country ASC, age DESC within",
                    "SELECT country, name, age FROM users ORDER BY country ASC, age DESC");
        }
    }

    private static void print(Statement stmt, String label, String sql) throws Exception {
        System.out.println("\n--- " + label + " ---");
        try (ResultSet rs = stmt.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) sb.append(rs.getString(i)).append("  ");
                System.out.println(sb);
            }
        }
    }
}
