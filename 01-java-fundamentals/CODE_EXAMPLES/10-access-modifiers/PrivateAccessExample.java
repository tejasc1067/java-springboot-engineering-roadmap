class Employee {

    // Private variable
    private double salary = 50000;

    // Public method to access private variable
    public void displaySalary() {

        System.out.println("Salary: " + salary);
    }
}

public class PrivateAccessExample {

    public static void main(String[] args) {

        Employee employee = new Employee();

        employee.displaySalary();
    }
}