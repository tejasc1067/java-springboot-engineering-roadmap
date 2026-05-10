class Animal {

    void eat() {

        System.out.println(
                "Animal Eats Food"
        );
    }
}

class Dog extends Animal {

    void bark() {

        System.out.println(
                "Dog Barks"
        );
    }
}

class Puppy extends Dog {

    void weep() {

        System.out.println(
                "Puppy Weeps"
        );
    }
}

public class MultilevelInheritanceExample {

    public static void main(String[] args) {

        Puppy puppy = new Puppy();

        puppy.eat();

        puppy.bark();

        puppy.weep();
    }
}