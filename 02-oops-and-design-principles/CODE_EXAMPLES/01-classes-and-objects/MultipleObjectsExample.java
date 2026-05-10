class Employee {

    int employeeId;

    String employeeName;

    void displayEmployee() {

        System.out.println(
                employeeId + " - " + employeeName
        );
    }
}

public class MultipleObjectsExample {

    public static void main(String[] args) {

        Employee employee1 = new Employee();

        employee1.employeeId = 1;

        employee1.employeeName = "Tejas";

        Employee employee2 = new Employee();

        employee2.employeeId = 2;

        employee2.employeeName = "Rahul";

        employee1.displayEmployee();

        employee2.displayEmployee();
    }
}