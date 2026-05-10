class Employee {

    void work() {

        System.out.println(
                "Employee Working"
        );
    }
}

class Developer extends Employee {

    @Override
    void work() {

        System.out.println(
                "Developer Writing Code"
        );
    }
}

public class OverrideAnnotationExample {

    public static void main(String[] args) {

        Developer developer = new Developer();

        developer.work();
    }
}