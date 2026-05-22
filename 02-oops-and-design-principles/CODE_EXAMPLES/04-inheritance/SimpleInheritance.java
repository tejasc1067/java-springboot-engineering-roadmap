// Dog extends Animal — Dog gets `name` and `eat()` for free, plus adds bark().
// The textbook is-a relationship: a Dog truly IS a kind of Animal.

class Animal {
    String name;

    void eat() {
        System.out.println(name + " is eating");
    }
}

class Dog extends Animal {
    void bark() {
        System.out.println(name + " is barking");
    }
}

public class SimpleInheritance {
    public static void main(String[] args) {
        Dog d = new Dog();
        d.name = "Rex";    // inherited field
        d.eat();           // inherited method
        d.bark();          // new method

        // A Dog can be referenced as an Animal — every Dog IS an Animal.
        Animal a = d;
        a.eat();
        // a.bark();    // ← won't compile; the Animal type doesn't know about bark().
    }
}
