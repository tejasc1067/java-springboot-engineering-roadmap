import java.util.ArrayList;
import java.util.List;

class Student {

    String name;

    Student(String name) {

        this.name = name;
    }
}

class Course {

    String courseName;

    Course(String courseName) {

        this.courseName = courseName;
    }
}

public class ManyToManyRelationshipExample {

    public static void main(String[] args) {

        Student student =
                new Student("Tejas");

        List<Course> courses =
                new ArrayList<>();

        courses.add(
                new Course("Java")
        );

        courses.add(
                new Course("Spring Boot")
        );

        System.out.println(
                student.name
                        + " enrolled in "
                        + courses.size()
                        + " courses"
        );
    }
}