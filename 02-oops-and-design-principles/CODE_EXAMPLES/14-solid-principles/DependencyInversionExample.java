interface Database {

    void connect();
}

class MySQLDatabase
        implements Database {

    @Override
    public void connect() {

        System.out.println(
                "Connected to MySQL"
        );
    }
}

class PostgreSQLDatabase
        implements Database {

    @Override
    public void connect() {

        System.out.println(
                "Connected to PostgreSQL"
        );
    }
}

class UserService {

    private final Database database;

    UserService(Database database) {

        this.database = database;
    }

    void startService() {

        database.connect();

        System.out.println(
                "User Service Started"
        );
    }
}

public class DependencyInversionExample {

    public static void main(String[] args) {

        Database database =
                new MySQLDatabase();

        UserService userService =
                new UserService(database);

        userService.startService();
    }
}