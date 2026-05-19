class Department {

    int departmentId;
    String departmentName;

    Department(
            int departmentId,
            String departmentName
    ) {

        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }
}

class Employee {

    int employeeId;
    String employeeName;
    int departmentId;

    Employee(
            int employeeId,
            String employeeName,
            int departmentId
    ) {

        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.departmentId = departmentId;
    }
}

public class ThirdNormalFormExample {

    public static void main(String[] args) {

        Department department =
                new Department(
                        1,
                        "Engineering"
                );

        Employee employee =
                new Employee(
                        101,
                        "Tejas",
                        1
                );

        System.out.println(
                employee.employeeName
                        + " works in "
                        + department.departmentName
        );
    }
}