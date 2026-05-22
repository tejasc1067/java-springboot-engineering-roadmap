// Two ways to give a Manager all the attributes of an Employee:
//   1. INHERITANCE — Manager extends Employee. Tight coupling; exposes everything.
//   2. COMPOSITION — Manager HAS-A Employee. Choose what to expose.
//
// Both are shown. Read both, then read "When to choose composition" at the bottom.

class Employee {
    String name;
    double salary;

    Employee(String name, double salary) {
        this.name = name;
        this.salary = salary;
    }

    void giveRaise(double amount) { salary += amount; }
    void fire()                   { System.out.println(name + " was fired."); }
}

// ----- Inheritance -----
class ManagerByInheritance extends Employee {
    int directReports;
    ManagerByInheritance(String name, double salary, int reports) {
        super(name, salary);
        this.directReports = reports;
    }
    // Inherits giveRaise() and fire() automatically — including fire(), which
    // we maybe didn't want exposed on a Manager publicly.
}

// ----- Composition -----
class ManagerByComposition {
    private final Employee profile;     // composes an Employee
    private int directReports;

    ManagerByComposition(Employee profile, int reports) {
        this.profile = profile;
        this.directReports = reports;
    }

    // Delegate only the things we want exposed.
    void giveRaise(double amount) { profile.giveRaise(amount); }
    String name()                 { return profile.name; }
    double salary()               { return profile.salary; }

    // fire() is NOT exposed — outside code can't fire a Manager through this API.
}

public class CompositionOverInheritance {
    public static void main(String[] args) {
        ManagerByInheritance i = new ManagerByInheritance("Alice", 100_000, 5);
        i.giveRaise(10_000);
        i.fire();   // exposed whether we like it or not

        ManagerByComposition c = new ManagerByComposition(
                new Employee("Bob", 100_000), 5);
        c.giveRaise(10_000);
        // c.fire();   // ← not available; ManagerByComposition controls its surface

        System.out.println("Inheritance manager: " + i.name + " salary " + i.salary);
        System.out.println("Composition manager: " + c.name() + " salary " + c.salary());

        // When to prefer composition:
        //   - You want fine-grained control over what's exposed.
        //   - The "is-a" relationship is shaky (a Manager is also kind of an Employee,
        //     but you don't want the FULL Employee surface).
        //   - You might swap the composed object (e.g. a MockEmployee for tests).
    }
}
