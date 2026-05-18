class User {

    int userId;
    String name;

    User(int userId, String name) {

        this.userId = userId;
        this.name = name;
    }
}

class Order {

    int orderId;
    int userId;

    Order(int orderId, int userId) {

        this.orderId = orderId;
        this.userId = userId;
    }
}

public class RelationalThinkingExample {

    public static void main(String[] args) {

        User user =
                new User(1, "Tejas");

        Order order =
                new Order(101, 1);

        System.out.println(
                user.name
                        + " placed order "
                        + order.orderId
        );
    }
}