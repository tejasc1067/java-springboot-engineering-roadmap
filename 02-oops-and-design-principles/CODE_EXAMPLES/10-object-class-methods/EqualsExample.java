class Employee {

    int employeeId;

    Employee(int employeeId) {

        this.employeeId = employeeId;
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

        Employee employee =
                (Employee) obj;

        return employeeId
                == employee.employeeId;
    }
}

public class EqualsExample {

    public static void main(String[] args) {

        Employee employee1 =
                new Employee(1);

        Employee employee2 =
                new Employee(1);

        System.out.println(
                employee1.equals(employee2)
        );
    }
}