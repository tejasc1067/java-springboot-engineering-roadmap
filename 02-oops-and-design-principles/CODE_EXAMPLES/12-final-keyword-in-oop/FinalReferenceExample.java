class Student {

    String name;

    Student(String name) {

        this.name = name;
    }
}

public class FinalReferenceExample {

    public static void main(String[] args) {

        final Student student =
                new Student("Tejas");

        student.name = "Rahul";

        System.out.println(
                student.name
        );

        // Not Allowed
        // student = new Student("Amit");
    }
}