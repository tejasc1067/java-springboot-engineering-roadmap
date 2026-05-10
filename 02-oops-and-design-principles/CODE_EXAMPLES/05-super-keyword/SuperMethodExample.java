class Employee {

    void work() {

        System.out.println(
                "Employee Works"
        );
    }
}

class Developer extends Employee {

    void work() {

        System.out.println(
                "Developer Writes Code"
        );
    }

    void showParentWork() {

        super.work();

        work();
    }
}

public class SuperMethodExample {

    public static void main(String[] args) {

        Developer developer = new Developer();

        developer.showParentWork();
    }
}