// `super` only reaches one level up. There's no super.super.
// Each level chains to its immediate parent.

class Animal {
    void describe() {
        System.out.println("Level 1: animal");
    }
}

class Mammal extends Animal {
    @Override
    void describe() {
        super.describe();
        System.out.println("Level 2: mammal");
    }
}

class Dog extends Mammal {
    @Override
    void describe() {
        super.describe();   // calls Mammal.describe, which calls Animal.describe
        System.out.println("Level 3: dog");
    }
}

public class SuperInThreeLevels {
    public static void main(String[] args) {
        new Dog().describe();
        // Output:
        //   Level 1: animal
        //   Level 2: mammal
        //   Level 3: dog
    }
}
