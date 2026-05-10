class Bird {

    void move() {

        System.out.println(
                "Bird Moves"
        );
    }
}

class Sparrow extends Bird {

    @Override
    void move() {

        System.out.println(
                "Sparrow Flies"
        );
    }
}

public class LiskovSubstitutionExample {

    public static void main(String[] args) {

        Bird bird =
                new Sparrow();

        bird.move();
    }
}