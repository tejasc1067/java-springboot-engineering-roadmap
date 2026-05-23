// A static field is ONE field shared across every instance of the class.
// Modify it through any instance (or through the class name) — everyone sees
// the change.

public class StaticFields {
    public static void main(String[] args) {

        Student a = new Student("alice");
        Student b = new Student("bob");

        // Access through the class name (preferred — makes "static" visible to readers)
        System.out.println("Student.school = " + Student.school);

        // Both students share the same school
        System.out.println("a.school = " + a.school);     // works, but reads misleadingly
        System.out.println("b.school = " + b.school);

        // Change it once — everyone sees the change.
        Student.school = "Roosevelt High";
        System.out.println("\nafter reassign:");
        System.out.println("a.school = " + a.school);
        System.out.println("b.school = " + b.school);

        // Instance fields stay per-object.
        System.out.println("\na.name = " + a.name);
        System.out.println("b.name = " + b.name);
    }
}

class Student {
    static String school = "Lincoln High";   // shared by all students
    String name;                              // per-student

    Student(String name) {
        this.name = name;
    }
}
