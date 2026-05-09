class Student {

    // Static variable shared by all objects
    static String collegeName = "ABC College";

    String studentName;

    Student(String studentName) {

        this.studentName = studentName;
    }

    void displayDetails() {

        System.out.println(studentName + " studies at " + collegeName);
    }
}

public class StaticVariableExample {

    public static void main(String[] args) {

        Student firstStudent = new Student("Tejas");

        Student secondStudent = new Student("Rahul");

        firstStudent.displayDetails();

        secondStudent.displayDetails();
    }
}