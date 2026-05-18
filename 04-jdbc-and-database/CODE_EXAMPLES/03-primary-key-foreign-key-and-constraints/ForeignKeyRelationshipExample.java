class Customer {

    int customerId;

    Customer(int customerId) {

        this.customerId = customerId;
    }
}

class Purchase {

    int orderId;
    int customerId;

    Purchase(
            int orderId,
            int customerId
    ) {

        this.orderId = orderId;
        this.customerId = customerId;
    }
}

public class ForeignKeyRelationshipExample {

    public static void main(String[] args) {

        Customer customer =
                new Customer(1);

        Purchase purchase =
                new Purchase(101, 1);

        System.out.println(
                "Purchase linked to customer: "
                        + purchase.customerId
        );
    }
}