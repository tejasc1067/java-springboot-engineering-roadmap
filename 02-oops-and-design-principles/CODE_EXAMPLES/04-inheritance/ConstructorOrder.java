// Construction proceeds top-down: superclass constructor finishes before
// subclass constructor starts.
//
// Java inserts an implicit super() at the top of every constructor that
// doesn't explicitly call super(...) or this(...).

class Animal {
    Animal() {
        System.out.println("  Animal() running");
    }
}

class Mammal extends Animal {
    Mammal() {
        // Implicit super() goes here
        System.out.println("  Mammal() running");
    }
}

class Dog extends Mammal {
    Dog() {
        // Implicit super() goes here
        System.out.println("  Dog() running");
    }
}

public class ConstructorOrder {
    public static void main(String[] args) {
        System.out.println("Creating new Dog():");
        new Dog();
        // Output order shows the chain:
        //   Animal() running
        //   Mammal() running
        //   Dog() running
    }
}
