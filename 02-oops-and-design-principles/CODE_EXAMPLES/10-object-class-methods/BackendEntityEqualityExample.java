class Customer {

    int customerId;

    String customerName;

    Customer(int customerId,
             String customerName) {

        this.customerId = customerId;

        this.customerName = customerName;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {

            return true;
        }

        if (obj == null
                || getClass() != obj.getClass()) {

            return false;
        }

        Customer customer =
                (Customer) obj;

        return customerId
                == customer.customerId;
    }

    @Override
    public int hashCode() {

        return Integer.hashCode(customerId);
    }

    @Override
    public String toString() {

        return "Customer{id="
                + customerId
                + ", name='"
                + customerName
                + "'}";
    }
}

public class BackendEntityEqualityExample {

    public static void main(String[] args) {

        Customer customer1 =
                new Customer(1, "Tejas");

        Customer customer2 =
                new Customer(1, "Tejas");

        System.out.println(
                customer1.equals(customer2)
        );

        System.out.println(
                customer1.hashCode()
        );

        System.out.println(
                customer1
        );
    }
}