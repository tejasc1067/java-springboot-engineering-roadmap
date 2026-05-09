class Employee {

    String employeeName;

    int employeeId;

    Employee() {

        // Calling parameterized constructor
        this("Default Employee", 100);
    }

    Employee(String employeeName, int employeeId) {

        this.employeeName = employeeName;

        this.employeeId = employeeId;
    }

    void displayDetails() {

        System.out.println(employeeName + " - " + employeeId);
    }
}

public class ConstructorChainingExample {

    public static void main(String[] args) {

        Employee employee = new Employee();

        employee.displayDetails();
    }
}