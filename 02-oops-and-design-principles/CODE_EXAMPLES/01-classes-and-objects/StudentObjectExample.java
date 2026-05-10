class Student {

    int id;

    String name;

    double marks;

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

public class StudentObjectExample {

    public static void main(String[] args) {

        Student student = new Student();

        student.id = 101;

        student.name = "Tejas";

        student.marks = 85.5;

        student.displayStudentInfo();
    }
}