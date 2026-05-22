// One fat interface forces every implementor to provide things they don't do.
// Small focused interfaces let each class implement only what's relevant.

// ---- VIOLATING: fat interface ----
interface WorkerFat {
    void work();
    void eat();
    void sleep();
}

class HumanFat implements WorkerFat {
    public void work()  { System.out.println("human working"); }
    public void eat()   { System.out.println("human eating");  }
    public void sleep() { System.out.println("human sleeping"); }
}

class RobotFat implements WorkerFat {
    public void work()  { System.out.println("robot working"); }
    public void eat()   { throw new UnsupportedOperationException("robots don't eat"); }
    public void sleep() { throw new UnsupportedOperationException("robots don't sleep"); }
}

// ---- COMPLIANT: split interfaces ----
interface Workable  { void work();  }
interface Eatable   { void eat();   }
interface Sleepable { void sleep(); }

class Human implements Workable, Eatable, Sleepable {
    public void work()  { System.out.println("human working"); }
    public void eat()   { System.out.println("human eating");  }
    public void sleep() { System.out.println("human sleeping"); }
}

class Robot implements Workable {
    public void work()  { System.out.println("robot working"); }
    // No eat / sleep — Robot honestly doesn't do those.
}

public class InterfaceSegregation {
    public static void main(String[] args) {
        // Fat interface: Robot has to fake methods it can't honestly implement.
        WorkerFat fatRobot = new RobotFat();
        fatRobot.work();
        try { fatRobot.eat(); } catch (Exception e) { System.out.println("fat robot eat: " + e.getMessage()); }

        // Split interfaces: methods that take Workable accept both. Methods that
        // take Eatable accept only humans (and other actually-eating things).
        Workable[] workers = { new Human(), new Robot() };
        for (Workable w : workers) w.work();

        Eatable[] eaters = { new Human() };   // Robot can't be here — compile-time safety
        for (Eatable e : eaters) e.eat();
    }
}
