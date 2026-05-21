import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Putting it all together. A single program that hits every important habit:
//   - Pooled connections via HikariCP
//   - PreparedStatement everywhere there are values
//   - try-with-resources on every JDBC resource
//   - Explicit transaction for multi-step writes
//   - Row-count verification
//   - SQLException wrapped in a meaningful application exception
public class FullProductionShape {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    record User(long id, String name, String email) {}

    static class UserRepoException extends RuntimeException {
        UserRepoException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static void main(String[] args) throws Exception {
        try (HikariDataSource pool = buildPool()) {

            bootstrap(pool);

            UserRepository repo = new UserRepository(pool);

            long aliceId = repo.create(new User(0, "Alice", "alice@example.com"));
            long bobId   = repo.create(new User(0, "Bob",   "bob@example.com"));

            // Transactional update affecting two rows together.
            repo.swapEmails(aliceId, bobId);

            for (User u : repo.findAll()) {
                System.out.println(u);
            }
        }
    }

    private static HikariDataSource buildPool() {
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(URL);
        c.setUsername("sa");
        c.setPassword("");
        c.setMaximumPoolSize(10);
        c.setConnectionTimeout(5000);
        c.setLeakDetectionThreshold(30_000);
        c.setPoolName("users-pool");
        return new HikariDataSource(c);
    }

    private static void bootstrap(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement s = conn.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(200) UNIQUE NOT NULL
                )
                """);
        }
    }

    static class UserRepository {
        private final DataSource ds;
        UserRepository(DataSource ds) { this.ds = ds; }

        long create(User u) {
            String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.name());
                ps.setString(2, u.email());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new UserRepoException("failed to create user " + u.name(), e);
            }
        }

        List<User> findAll() {
            List<User> result = new ArrayList<>();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, name, email FROM users ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new User(rs.getLong("id"), rs.getString("name"), rs.getString("email")));
                }
                return result;
            } catch (SQLException e) {
                throw new UserRepoException("failed to list users", e);
            }
        }

        // Transactional: swap two users' emails atomically.
        // Trickier than it looks because of the UNIQUE constraint — we use a
        // temporary value to avoid a momentary uniqueness violation.
        void swapEmails(long idA, long idB) {
            try (Connection conn = ds.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement read = conn.prepareStatement("SELECT email FROM users WHERE id = ?");
                     PreparedStatement write = conn.prepareStatement("UPDATE users SET email = ? WHERE id = ?")) {

                    String emailA = readEmail(read, idA);
                    String emailB = readEmail(read, idB);

                    // Park B under a temporary email to free up emailB's value.
                    write.setString(1, "__tmp__" + System.nanoTime());
                    write.setLong(2, idB);
                    check(write.executeUpdate() == 1, "user B not found");

                    write.setString(1, emailB);
                    write.setLong(2, idA);
                    check(write.executeUpdate() == 1, "user A not found");

                    write.setString(1, emailA);
                    write.setLong(2, idB);
                    check(write.executeUpdate() == 1, "user B not found on second update");

                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw new UserRepoException("failed to swap emails", e);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new UserRepoException("failed to swap emails (connection)", e);
            }
        }

        private String readEmail(PreparedStatement read, long id) throws SQLException {
            read.setLong(1, id);
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("no user " + id);
                return rs.getString(1);
            }
        }

        private void check(boolean condition, String message) {
            if (!condition) throw new IllegalStateException(message);
        }
    }
}
