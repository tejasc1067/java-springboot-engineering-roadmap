class Customer {

    int customerId;
    String name;

    Customer(int customerId, String name) {

        this.customerId = customerId;
        this.name = name;
    }
}

class PurchaseOrder {

    int orderId;
    int customerId;

    PurchaseOrder(
            int orderId,
            int customerId
    ) {

        this.orderId = orderId;
        this.customerId = customerId;
    }
}

public class RelationalDatabaseSimulation {

    public static void main(String[] args) {

        Customer customer =
                new Customer(1, "Tejas");

        PurchaseOrder order =
                new PurchaseOrder(101, 1);

        System.out.println(
                customer.name
                        + " placed order "
                        + order.orderId
        );
    }
}