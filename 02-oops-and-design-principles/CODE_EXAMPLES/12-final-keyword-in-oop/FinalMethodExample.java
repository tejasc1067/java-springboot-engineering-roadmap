class Employee {

    final void companyPolicy() {

        System.out.println(
                "Company Policy Cannot Be Overridden"
        );
    }
}

class Developer extends Employee {

    // Not Allowed
    // void companyPolicy() {
    // }
}

public class FinalMethodExample {

    public static void main(String[] args) {

        Developer developer =
                new Developer();

        developer.companyPolicy();
    }
}