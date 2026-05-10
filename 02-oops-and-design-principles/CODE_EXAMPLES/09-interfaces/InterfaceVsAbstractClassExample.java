interface Flyable {

    void fly();
}

abstract class Bird {

    void eat() {

        System.out.println(
                "Bird Eats Food"
        );
    }
}

class Eagle extends Bird
        implements Flyable {

    @Override
    public void fly() {

        System.out.println(
                "Eagle Flies"
        );
    }
}

public class InterfaceVsAbstractClassExample {

    public static void main(String[] args) {

        Eagle eagle = new Eagle();

        eagle.eat();

        eagle.fly();
    }
}