class Employee {

    String name =
            "Tejas";
}

public class HeapVsStackExample {

    public static void main(String[] args) {

        int localVariable = 100;

        Employee employee =
                new Employee();

        System.out.println(
                localVariable
        );

        System.out.println(
                employee.name
        );
    }
}