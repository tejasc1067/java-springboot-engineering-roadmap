class Address {

    String city;

    Address(String city) {

        this.city = city;
    }
}

class User {

    String username;

    Address address;

    User(
            String username,
            Address address
    ) {

        this.username = username;

        this.address = address;
    }

    void displayUserInfo() {

        System.out.println(
                username
                        + " lives in "
                        + address.city
        );
    }
}

public class HasARelationshipExample {

    public static void main(String[] args) {

        Address address =
                new Address("Pune");

        User user =
                new User(
                        "Tejas",
                        address
                );

        user.displayUserInfo();
    }
}