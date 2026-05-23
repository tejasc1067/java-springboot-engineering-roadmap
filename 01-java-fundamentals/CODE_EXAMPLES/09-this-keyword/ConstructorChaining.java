// Multiple constructors that all funnel into one "real" constructor via
// this(...). Avoids repeating the field-assignment logic in every variant.

public class ConstructorChaining {
    public static void main(String[] args) {

        new Pupil().describe();
        new Pupil("alice").describe();
        new Pupil("bob", 22).describe();
        new Pupil("carol", 21, "physics").describe();

        // Rule: this(...) must be the FIRST statement in a constructor.
        // Try moving any code above it in any of the constructors below —
        // the compiler will reject it with:
        //   "call to this must be first statement in constructor"
    }
}

// Named Pupil here so we don't clash with the Student class declared in
// other single-file demos in this folder.
class Pupil {
    String name;
    int age;
    String major;

    Pupil() {
        this("unknown", 0, "undeclared");
    }

    Pupil(String name) {
        this(name, 0, "undeclared");
    }

    Pupil(String name, int age) {
        this(name, age, "undeclared");
    }

    // The one that actually does the work.
    Pupil(String name, int age, String major) {
        this.name = name;
        this.age = age;
        this.major = major;
    }

    void describe() {
        System.out.println(name + " | age " + age + " | major " + major);
    }
}
