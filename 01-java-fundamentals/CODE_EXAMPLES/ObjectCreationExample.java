class Student {

    int id;

    Student(int id) {

        this.id = id;

        System.out.println(
                "Object Created with ID: "
                        + id
        );
    }
}

public class ObjectCreationExample {

    public static void main(String[] args) {

        Student student1 = new Student(101);

        Student student2 = new Student(102);
    }
}