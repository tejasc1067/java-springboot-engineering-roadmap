class Customer {

    int customerId;

    String customerName;

    Customer() {

        this(101);

        System.out.println(
                "Default Constructor Executed"
        );
    }

    Customer(int customerId) {

        this(customerId, "Tejas");

        System.out.println(
                "Single Parameter Constructor Executed"
        );
    }

    Customer(int customerId, String customerName) {

        this.customerId = customerId;

        this.customerName = customerName;

        System.out.println(
                "Final Constructor Executed"
        );
    }
}

public class ConstructorChainingExample {

    public static void main(String[] args) {

        Customer customer = new Customer();
    }
}