class Person {

    Person() {

        System.out.println(
                "Parent Constructor Executed"
        );
    }
}

class Student extends Person {

    Student() {

        super();

        System.out.println(
                "Child Constructor Executed"
        );
    }
}

public class SuperConstructorExample {

    public static void main(String[] args) {

        Student student = new Student();
    }
}