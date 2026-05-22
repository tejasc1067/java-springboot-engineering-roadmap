// A class can implement many interfaces. This is Java's answer to multiple
// inheritance — without the ambiguity of multiple parent classes.

interface Swimmer { void swim(); }
interface Flyer   { void fly();  }

class Duck implements Swimmer, Flyer {
    public void swim() { System.out.println("duck: paddling"); }
    public void fly()  { System.out.println("duck: flapping"); }
}

class Penguin implements Swimmer {                   // not a Flyer
    public void swim() { System.out.println("penguin: torpedoing"); }
}

public class MultipleInterfaces {

    static void racePool(Swimmer s)  { s.swim(); }
    static void crossSky(Flyer f)    { f.fly();  }

    public static void main(String[] args) {
        Duck d = new Duck();
        Penguin p = new Penguin();

        racePool(d);   // works — Duck is a Swimmer
        racePool(p);   // works — Penguin is a Swimmer
        crossSky(d);   // works — Duck is a Flyer
        // crossSky(p);  // won't compile — Penguin is not a Flyer
    }
}
