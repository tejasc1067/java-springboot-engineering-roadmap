import java.util.ArrayList;
import java.util.Collections;

class Employee implements Comparable<Employee> {

    int employeeId;

    String employeeName;

    Employee(int employeeId, String employeeName) {

        this.employeeId = employeeId;

        this.employeeName = employeeName;
    }

    @Override
    public int compareTo(Employee otherEmployee) {

        return this.employeeId - otherEmployee.employeeId;
    }

    @Override
    public String toString() {

        return employeeId + " - " + employeeName;
    }
}

public class ComparableSortingExample {

    public static void main(String[] args) {

        ArrayList<Employee> employees = new ArrayList<>();

        employees.add(new Employee(103, "Rahul"));

        employees.add(new Employee(101, "Tejas"));

        employees.add(new Employee(102, "Amit"));

        Collections.sort(employees);

        System.out.println(employees);
    }
}