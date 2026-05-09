import java.util.ArrayList;

class Student {

    int id;

    String name;

    Student(int id, String name) {

        this.id = id;

        this.name = name;
    }
}

public class CustomObjectCollectionExample {

    public static void main(String[] args) {

        ArrayList<Student> students = new ArrayList<>();

        students.add(new Student(101, "Tejas"));

        students.add(new Student(102, "Rahul"));

        students.add(new Student(103, "Amit"));

        for (Student student : students) {

            System.out.println(
                    student.id + " - " + student.name
            );
        }
    }
}