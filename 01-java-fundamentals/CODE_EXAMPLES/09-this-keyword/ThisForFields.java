// The most common use of `this`: a parameter has the same name as a field,
// and we need to disambiguate which one is which.

public class ThisForFields {
    public static void main(String[] args) {

        Student a = new Student("alice", 20);
        Student b = new Student("bob", 22);
        a.describe();
        b.describe();

        // The silent bug to know about — without `this.`, the parameter assigns
        // to itself and the field stays null. Try uncommenting the broken
        // constructor and watch the output.
        //
        //   Student bad = new Student("carol", 18);
        //   bad.describe();   // would print "null (age 0)" with the buggy ctor
    }
}

class Student {
    String name;
    int age;

    Student(String name, int age) {
        this.name = name;     // this.name = the field; name = the parameter
        this.age  = age;
    }

    void describe() {
        System.out.println(this.name + " (age " + this.age + ")");
    }
}
