class Employee {

    int id;
    String name;

    Employee(int id, String name) {

        this.id = id;
        this.name = name;
    }
}

class Salary {

    int employeeId;
    double amount;

    Salary(int employeeId, double amount) {

        this.employeeId = employeeId;
        this.amount = amount;
    }
}

public class RightJoinExample {

    public static void main(String[] args) {

        Employee employee = null;

        Salary salary =
                new Salary(1, 75000);

        System.out.println(
                employee == null
                        ? "Salary Exists Without Employee Data"
                        : employee.name
        );
    }
}