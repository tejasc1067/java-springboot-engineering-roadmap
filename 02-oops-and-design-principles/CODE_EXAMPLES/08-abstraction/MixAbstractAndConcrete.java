// An abstract class can mix abstract methods (must implement) with concrete
// ones (already implemented). That's why abstract classes earn their existence —
// they share real code across subclasses.

abstract class Vehicle {
    String name;
    int wheels;

    Vehicle(String name, int wheels) {
        this.name = name;
        this.wheels = wheels;
    }

    // Concrete — every Vehicle does this the same way.
    void describe() {
        System.out.println(name + " (" + wheels + " wheels, " + maxSpeed() + " mph max)");
    }

    void honk() {
        System.out.println(name + " goes BEEP");
    }

    // Abstract — varies by vehicle type.
    abstract int maxSpeed();
}

class Car extends Vehicle {
    Car(String name) {
        super(name, 4);
    }
    @Override int maxSpeed() { return 120; }
}

class Bicycle extends Vehicle {
    Bicycle(String name) {
        super(name, 2);
    }
    @Override int maxSpeed() { return 25; }
}

public class MixAbstractAndConcrete {
    public static void main(String[] args) {
        Vehicle[] fleet = { new Car("Civic"), new Bicycle("Schwinn") };
        for (Vehicle v : fleet) {
            v.describe();
            v.honk();
        }
    }
}
