class StudentCourse {

    int studentId;
    int courseId;
    String courseName;

    StudentCourse(
            int studentId,
            int courseId,
            String courseName
    ) {

        this.studentId = studentId;
        this.courseId = courseId;
        this.courseName = courseName;
    }
}

public class SecondNormalFormExample {

    public static void main(String[] args) {

        StudentCourse record =
                new StudentCourse(
                        1,
                        101,
                        "Java"
                );

        System.out.println(
                record.courseName
        );
    }
}