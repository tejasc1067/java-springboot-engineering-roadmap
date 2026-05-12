class Employee {

    private final int id;

    Employee(int id) {

        this.id = id;
    }

    @Override
    public int hashCode() {

        return id;
    }
}

public class HashMapInternalFlowExample {

    public static void main(String[] args) {

        Employee employee =
                new Employee(101);

        System.out.println(
                employee.hashCode()
        );
    }
}