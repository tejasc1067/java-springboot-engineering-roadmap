import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Load credentials from environment variables instead of hardcoding them.
// This is what every production app does — values come from the deploy
// environment (Kubernetes secrets, AWS Secrets Manager, .env files in dev).
//
// To try this:
//   On macOS/Linux:
//     export DB_URL=jdbc:h2:mem:demo
//     export DB_USER=sa
//     export DB_PASSWORD=
//   On Windows (PowerShell):
//     $env:DB_URL = "jdbc:h2:mem:demo"
//     $env:DB_USER = "sa"
//     $env:DB_PASSWORD = ""
//   Then run.
//
// If any required env var is missing, the program fails fast with a clear
// error — better than silently using a default that "works in dev."
public class CredentialsFromEnvironment {

    public static void main(String[] args) throws Exception {
        String url  = require("DB_URL");
        String user = require("DB_USER");
        String pass = optional("DB_PASSWORD", "");   // some local DBs allow empty

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            rs.next();
            System.out.println("Connected via env-supplied credentials. SELECT 1 → " + rs.getInt(1));
        }
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable " + name + " is not set. " +
                    "See file header for how to set it.");
        }
        return value;
    }

    private static String optional(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null) ? defaultValue : value;
    }
}
