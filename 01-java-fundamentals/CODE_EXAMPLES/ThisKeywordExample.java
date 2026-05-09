class Student {

    String studentName;

    Student(String studentName) {

        // this refers to current object
        this.studentName = studentName;
    }

    void displayDetails() {

        System.out.println("Student Name: " + this.studentName);
    }
}

public class ThisKeywordExample {

    public static void main(String[] args) {

        Student student = new Student("Tejas");

        student.displayDetails();
    }
}