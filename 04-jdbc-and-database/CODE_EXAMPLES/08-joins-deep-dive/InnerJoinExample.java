class User {

    int id;
    String name;

    User(int id, String name) {

        this.id = id;
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

public class InnerJoinExample {

    public static void main(String[] args) {

        User user =
                new User(1, "Tejas");

        Order order =
                new Order(101, 1);

        if (user.id == order.userId) {

            System.out.println(
                    user.name
                            + " -> Order "
                            + order.orderId
            );
        }
    }
}