class Student {

    int id;

    String name;

    Student(int id, String name) {

        this.id = id;

        this.name = name;
    }

    @Override
    public String toString() {

        return "Student{id="
                + id
                + ", name='"
                + name
                + "'}";
    }
}

public class ToStringExample {

    public static void main(String[] args) {

        Student student =
                new Student(101, "Tejas");

        System.out.println(student);
    }
}