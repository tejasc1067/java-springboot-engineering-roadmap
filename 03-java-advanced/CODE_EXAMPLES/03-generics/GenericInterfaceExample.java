interface Repository<T> {

    void save(T data);
}

class UserRepository
        implements Repository<String> {

    @Override
    public void save(String data) {

        System.out.println(
                "Saved User: "
                        + data
        );
    }
}

public class GenericInterfaceExample {

    public static void main(String[] args) {

        UserRepository repository =
                new UserRepository();

        repository.save("Tejas");
    }
}