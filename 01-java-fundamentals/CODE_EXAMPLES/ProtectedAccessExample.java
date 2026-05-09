class Company {

    // Protected variable
    protected String companyName = "Tech Solutions";
}

class Employee extends Company {

    void displayCompany() {

        System.out.println("Company Name: " + companyName);
    }
}

public class ProtectedAccessExample {

    public static void main(String[] args) {

        Employee employee = new Employee();

        employee.displayCompany();
    }
}