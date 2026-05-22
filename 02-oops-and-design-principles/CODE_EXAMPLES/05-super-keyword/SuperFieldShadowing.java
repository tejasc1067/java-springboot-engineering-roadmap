// Don't write code like this in real life — it's confusing.
// Shown here so you know what super.field does and can recognize the smell.
//
// When a subclass declares a field with the same name as a parent field, the
// subclass field "hides" the parent's. The two fields coexist; neither is
// removed. `this.field` reads the subclass's; `super.field` reads the parent's.

class Animal {
    String type = "Animal";
}

class Dog extends Animal {
    String type = "Dog";   // shadows Animal.type — almost always a mistake

    void describe() {
        System.out.println("this.type  = " + this.type);    // "Dog"
        System.out.println("super.type = " + super.type);   // "Animal"
    }
}

public class SuperFieldShadowing {
    public static void main(String[] args) {
        new Dog().describe();

        // If you find yourself reaching for super.field, ask:
        //   - Should the subclass field have a different name?
        //   - Should the subclass field exist at all?
        // The answer is almost always one of those, not "keep super.field".
    }
}
