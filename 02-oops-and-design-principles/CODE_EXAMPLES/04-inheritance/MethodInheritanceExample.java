class Employee {

    void displayCompany() {

        System.out.println(
                "Company: ABC Company"
        );
    }
}

class Developer extends Employee {

    void writeCode() {

        System.out.println(
                "Developer Writes Code"
        );
    }
}

public class MethodInheritanceExample {

    public static void main(String[] args) {

        Developer developer = new Developer();

        developer.displayCompany();

        developer.writeCode();
    }
}