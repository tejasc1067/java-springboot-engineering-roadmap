// The Spring pattern. UserService depends on the UserRepository INTERFACE,
// not on any specific implementation. The actual repository is supplied via
// the constructor — by a test, by the framework, by configuration.
//
// This is dependency injection (DI) — the runtime mechanism behind the
// dependency inversion principle.

interface UserRepository {
    String findById(long id);
}

// "Real" implementation — would talk to a database in production.
class MySqlUserRepository implements UserRepository {
    public String findById(long id) {
        return "User#" + id + " (from MySQL)";
    }
}

// "Test" implementation — in-memory; no infrastructure required.
class InMemoryUserRepository implements UserRepository {
    public String findById(long id) {
        return "User#" + id + " (in-memory fake)";
    }
}

class UserService {
    private final UserRepository repo;

    UserService(UserRepository repo) {       // ← receives the abstraction
        this.repo = repo;
    }

    String describeUser(long id) {
        return "Describing: " + repo.findById(id);
    }
}

public class DependencyInversion {
    public static void main(String[] args) {
        // Production wiring — pass the real repo.
        UserService prod = new UserService(new MySqlUserRepository());
        System.out.println(prod.describeUser(42));

        // Test wiring — pass the fake repo. UserService doesn't change.
        UserService test = new UserService(new InMemoryUserRepository());
        System.out.println(test.describeUser(42));

        // Spring's @Autowired does the wiring for you; the pattern is the same.
        // Without DIP, UserService would be `new MySqlUserRepository()` inside the
        // service — bolted to one implementation forever, untestable, unswappable.
    }
}
