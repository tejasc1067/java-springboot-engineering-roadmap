import java.util.ArrayList;
import java.util.List;

class Customer {

    int customerId;
    String name;

    Customer(int customerId, String name) {

        this.customerId = customerId;
        this.name = name;
    }
}

class Order {

    int orderId;

    Order(int orderId) {

        this.orderId = orderId;
    }
}

public class OneToManyRelationshipExample {

    public static void main(String[] args) {

        Customer customer =
                new Customer(1, "Tejas");

        List<Order> orders =
                new ArrayList<>();

        orders.add(new Order(101));
        orders.add(new Order(102));

        System.out.println(
                customer.name
                        + " placed "
                        + orders.size()
                        + " orders"
        );
    }
}