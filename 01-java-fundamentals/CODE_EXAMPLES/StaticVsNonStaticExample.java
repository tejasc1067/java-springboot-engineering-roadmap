class Employee {

    // Static variable
    static String companyName = "Tech Company";

    // Non-static variable
    String employeeName;

    Employee(String employeeName) {

        this.employeeName = employeeName;
    }

    void displayDetails() {

        System.out.println(employeeName + " works at " + companyName);
    }
}

public class StaticVsNonStaticExample {

    public static void main(String[] args) {

        Employee firstEmployee = new Employee("Tejas");

        Employee secondEmployee = new Employee("Amit");

        firstEmployee.displayDetails();

        secondEmployee.displayDetails();
    }
}