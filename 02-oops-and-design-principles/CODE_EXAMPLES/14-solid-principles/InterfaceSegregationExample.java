interface Workable {

    void work();
}

interface Eatable {

    void eat();
}

class Developer
        implements Workable, Eatable {

    @Override
    public void work() {

        System.out.println(
                "Developer Writes Code"
        );
    }

    @Override
    public void eat() {

        System.out.println(
                "Developer Eats Food"
        );
    }
}

public class InterfaceSegregationExample {

    public static void main(String[] args) {

        Developer developer =
                new Developer();

        developer.work();

        developer.eat();
    }
}