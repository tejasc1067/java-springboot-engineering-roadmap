import java.io.Serializable;

class Customer implements Serializable {

    private String username;

    private transient String password;

    public Customer(
            String username,
            String password
    ) {

        this.username = username;
        this.password = password;
    }

    @Override
    public String toString() {

        return username + " " + password;
    }
}

public class TransientKeywordExample {

    public static void main(String[] args) {

        Customer customer =
                new Customer(
                        "tejas",
                        "secret123"
                );

        System.out.println(customer);
    }
}