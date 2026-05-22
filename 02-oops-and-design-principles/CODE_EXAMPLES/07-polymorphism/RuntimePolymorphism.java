// One loop, one method call, three different behaviors at runtime.
// The variable is typed Animal; the actual object decides which speak() runs.

class Animal {
    String name;
    Animal(String name) { this.name = name; }
    void speak() { System.out.println(name + ": animal sound"); }
}

class Dog extends Animal {
    Dog(String name) { super(name); }
    @Override void speak() { System.out.println(name + ": Woof"); }
}

class Cat extends Animal {
    Cat(String name) { super(name); }
    @Override void speak() { System.out.println(name + ": Meow"); }
}

class Cow extends Animal {
    Cow(String name) { super(name); }
    @Override void speak() { System.out.println(name + ": Moo"); }
}

public class RuntimePolymorphism {
    public static void main(String[] args) {
        Animal[] zoo = {
                new Dog("Rex"),
                new Cat("Whiskers"),
                new Cow("Bessie"),
                new Animal("Generic")
        };

        for (Animal a : zoo) {
            a.speak();   // each call dispatches to the actual object's version
        }
    }
}
