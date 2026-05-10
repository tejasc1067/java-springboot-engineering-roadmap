class Employee {

    String employeeName;

    Employee(String employeeName) {

        this.employeeName = employeeName;
    }
}

class Department {

    String departmentName;

    Employee employee;

    Department(
            String departmentName,
            Employee employee
    ) {

        this.departmentName =
                departmentName;

        this.employee = employee;
    }

    void displayDepartmentInfo() {

        System.out.println(
                departmentName
                        + " department has employee "
                        + employee.employeeName
        );
    }
}

public class AggregationExample {

    public static void main(String[] args) {

        Employee employee =
                new Employee("Tejas");

        Department department =
                new Department(
                        "Engineering",
                        employee
                );

        department.displayDepartmentInfo();
    }
}