import java.sql.SQLException;

public class ExceptionTranslation {

    static class DataAccessException extends RuntimeException {
        public DataAccessException(String msg, Throwable cause) { super(msg, cause); }
    }

    static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String msg, Throwable cause) { super(msg, cause); }
    }

    static class JdbcLayer {
        static String fetchUser(long id) throws SQLException {
            throw new SQLException("ORA-00942: table or view does not exist");
        }
    }

    static class UserRepository {
        String findById(long id) {
            try {
                return JdbcLayer.fetchUser(id);
            } catch (SQLException e) {
                throw new DataAccessException("failed to query user " + id, e);
            }
        }
    }

    static class UserService {
        private final UserRepository repo = new UserRepository();

        String getUser(long id) {
            try {
                return repo.findById(id);
            } catch (DataAccessException e) {
                throw new UserNotFoundException("user " + id + " not retrievable", e);
            }
        }
    }

    public static void main(String[] args) {
        UserService service = new UserService();
        try {
            service.getUser(42L);
        } catch (UserNotFoundException e) {
            e.printStackTrace(System.out);
        }
    }
}
