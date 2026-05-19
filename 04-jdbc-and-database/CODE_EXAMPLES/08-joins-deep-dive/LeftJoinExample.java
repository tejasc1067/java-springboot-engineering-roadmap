class Customer {

    int id;
    String name;

    Customer(int id, String name) {

        this.id = id;
        this.name = name;
    }
}

class Purchase {

    int orderId;
    int customerId;

    Purchase(int orderId, int customerId) {

        this.orderId = orderId;
        this.customerId = customerId;
    }
}

public class LeftJoinExample {

    public static void main(String[] args) {

        Customer customer =
                new Customer(1, "Tejas");

        Purchase purchase = null;

        System.out.println(
                customer.name
                        + " -> "
                        + (purchase == null
                        ? "No Orders"
                        : purchase.orderId)
        );
    }
}