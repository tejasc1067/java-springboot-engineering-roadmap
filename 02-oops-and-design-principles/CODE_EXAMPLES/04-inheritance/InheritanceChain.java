// Three-level hierarchy: Animal → Mammal → Dog. Each level adds something.
// Be wary of going much deeper than this in real code — deep hierarchies are
// hard to reason about.

class Animal {
    String name;

    void eat() {
        System.out.println(name + " is eating");
    }
}

class Mammal extends Animal {
    int legs = 4;

    void describe() {
        System.out.println(name + " is a mammal with " + legs + " legs");
    }
}

class Dog extends Mammal {
    void bark() {
        System.out.println(name + " is barking");
    }
}

public class InheritanceChain {
    public static void main(String[] args) {
        Dog d = new Dog();
        d.name = "Rex";      // from Animal
        d.legs = 4;          // from Mammal
        d.eat();             // from Animal
        d.describe();        // from Mammal
        d.bark();            // from Dog

        // Polymorphism preview (topic 07): the same object can be viewed at
        // any level of the hierarchy.
        Mammal m = d;        m.describe();
        Animal a = d;        a.eat();
    }
}
