class Student {

    // Default access variable
    String studentName = "Tejas";

    void displayStudent() {

        System.out.println("Student Name: " + studentName);
    }
}

public class DefaultAccessExample {

    public static void main(String[] args) {

        Student student = new Student();

        student.displayStudent();
    }
}