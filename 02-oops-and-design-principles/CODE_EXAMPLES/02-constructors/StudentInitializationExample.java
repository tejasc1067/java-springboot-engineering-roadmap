class Student {

    int id;

    String name;

    double marks;

    Student(int id, String name, double marks) {

        this.id = id;

        this.name = name;

        this.marks = marks;
    }

    void displayStudentInfo() {

        System.out.println(
                "ID: " + id
        );

        System.out.println(
                "Name: " + name
        );

        System.out.println(
                "Marks: " + marks
        );
    }
}

public class StudentInitializationExample {

    public static void main(String[] args) {

        Student student =
                new Student(
                        101,
                        "Tejas",
                        88.5
                );

        student.displayStudentInfo();
    }
}