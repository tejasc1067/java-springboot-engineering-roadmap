import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// DDL example: CREATE a table, then read the database's own metadata to
// confirm what we created. Demonstrates that the database knows the table's
// structure once DDL has been executed.
public class CreateAndDescribe {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            // DDL: define the structure.
            // executeUpdate is used for statements that don't return rows
            // (CREATE, INSERT, UPDATE, DELETE).
            stmt.executeUpdate("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    age INT
                )
                """);

            System.out.println("Table 'users' created.");

            // Read the database's own metadata to confirm the columns exist.
            // This is the "I want to see what I just made" check — useful
            // any time you're not sure what's in a schema.
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet cols = meta.getColumns(null, null, "USERS", null)) {
                System.out.println("Columns:");
                while (cols.next()) {
                    System.out.printf("  %-10s %s%n",
                            cols.getString("COLUMN_NAME"),
                            cols.getString("TYPE_NAME"));
                }
            }
        }
    }
}
