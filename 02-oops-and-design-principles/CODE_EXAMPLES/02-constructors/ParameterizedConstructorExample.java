class Student {

    int id;

    String name;

    Student(int id, String name) {

        this.id = id;

        this.name = name;
    }

    void displayStudent() {

        System.out.println(
                "ID: " + id
        );

        System.out.println(
                "Name: " + name
        );
    }
}

public class ParameterizedConstructorExample {

    public static void main(String[] args) {

        Student student =
                new Student(101, "Tejas");

        student.displayStudent();
    }
}