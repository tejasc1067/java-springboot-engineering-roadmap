final class Student {

    private final int rollNumber;

    private final String studentName;

    Student(int rollNumber,
            String studentName) {

        this.rollNumber =
                rollNumber;

        this.studentName =
                studentName;
    }

    public int getRollNumber() {

        return rollNumber;
    }

    public String getStudentName() {

        return studentName;
    }
}

public class ImmutableStudentExample {

    public static void main(String[] args) {

        Student student =
                new Student(
                        1,
                        "Tejas"
                );

        System.out.println(
                student.getRollNumber()
        );

        System.out.println(
                student.getStudentName()
        );
    }
}