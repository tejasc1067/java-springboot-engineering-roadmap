class Customer {

    String customerName;

    Customer(String customerName) {

        this.customerName = customerName;
    }
}

class Order {

    int orderId;

    Customer customer;

    Order(
            int orderId,
            Customer customer
    ) {

        this.orderId = orderId;

        this.customer = customer;
    }

    void displayOrderInfo() {

        System.out.println(
                "Order ID: "
                        + orderId
        );

        System.out.println(
                "Customer: "
                        + customer.customerName
        );
    }
}

public class BackendEntityRelationshipExample {

    public static void main(String[] args) {

        Customer customer =
                new Customer("Tejas");

        Order order =
                new Order(
                        101,
                        customer
                );

        order.displayOrderInfo();
    }
}