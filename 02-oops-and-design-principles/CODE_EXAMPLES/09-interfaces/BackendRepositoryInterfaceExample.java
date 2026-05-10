interface UserRepository {

    void saveUser();
}

class MySQLUserRepository
        implements UserRepository {

    @Override
    public void saveUser() {

        System.out.println(
                "User Saved in MySQL Database"
        );
    }
}

class PostgreSQLUserRepository
        implements UserRepository {

    @Override
    public void saveUser() {

        System.out.println(
                "User Saved in PostgreSQL Database"
        );
    }
}

public class BackendRepositoryInterfaceExample {

    public static void main(String[] args) {

        UserRepository repository;

        repository =
                new MySQLUserRepository();

        repository.saveUser();

        repository =
                new PostgreSQLUserRepository();

        repository.saveUser();
    }
}