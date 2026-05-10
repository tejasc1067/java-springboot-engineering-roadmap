class Employee {

    private int employeeId;

    private String employeeName;

    public void assignEmployeeDetails(
            int employeeId,
            String employeeName
    ) {

        this.employeeId = employeeId;

        this.employeeName = employeeName;
    }

    public void displayEmployeeDetails() {

        System.out.println(
                "Employee ID: "
                        + employeeId
        );

        System.out.println(
                "Employee Name: "
                        + employeeName
        );
    }
}

public class BasicEncapsulationExample {

    public static void main(String[] args) {

        Employee employee = new Employee();

        employee.assignEmployeeDetails(
                101,
                "Tejas"
        );

        employee.displayEmployeeDetails();
    }
}