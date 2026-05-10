class Bank {

    String bankName;

    Bank(String bankName) {

        this.bankName = bankName;
    }
}

class Customer {

    String customerName;

    Customer(String customerName) {

        this.customerName = customerName;
    }

    void useBank(Bank bank) {

        System.out.println(
                customerName
                        + " uses "
                        + bank.bankName
        );
    }
}

public class AssociationExample {

    public static void main(String[] args) {

        Bank bank =
                new Bank("HDFC");

        Customer customer =
                new Customer("Tejas");

        customer.useBank(bank);
    }
}