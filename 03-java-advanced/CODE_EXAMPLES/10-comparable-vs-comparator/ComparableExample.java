import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Employee
        implements Comparable<Employee> {

    private final int id;

    private final String name;

    Employee(int id,
             String name) {

        this.id = id;

        this.name = name;
    }

    @Override
    public int compareTo(Employee employee) {

        return Integer.compare(
                this.id,
                employee.id
        );
    }

    @Override
    public String toString() {

        return id + " - " + name;
    }
}

public class ComparableExample {

    public static void main(String[] args) {

        List<Employee> employees =
                new ArrayList<>();

        employees.add(
                new Employee(103, "Amit")
        );

        employees.add(
                new Employee(101, "Tejas")
        );

        employees.add(
                new Employee(102, "Rahul")
        );

        Collections.sort(employees);

        System.out.println(employees);
    }
}