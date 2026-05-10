abstract class Employee {

    private int employeeId;

    Employee(int employeeId) {

        this.employeeId = employeeId;
    }

    int getEmployeeId() {

        return employeeId;
    }

    abstract void work();
}

class Developer extends Employee {

    Developer(int employeeId) {

        super(employeeId);
    }

    @Override
    void work() {

        System.out.println(
                "Developer Writes Code"
        );
    }
}

public class AbstractionVsEncapsulationExample {

    public static void main(String[] args) {

        Developer developer =
                new Developer(101);

        System.out.println(
                "Employee ID: "
                        + developer.getEmployeeId()
        );

        developer.work();
    }
}