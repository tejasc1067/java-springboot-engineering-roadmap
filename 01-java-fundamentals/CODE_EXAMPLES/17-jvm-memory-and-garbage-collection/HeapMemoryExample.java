class Employee {

    String name = "Tejas";
}

public class HeapMemoryExample {

    public static void main(String[] args) {

        Employee employee = new Employee();

        System.out.println(
                "Object Stored in Heap: "
                        + employee.name
        );
    }
}