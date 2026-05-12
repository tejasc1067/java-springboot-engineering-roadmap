class Product {

    private final int id;

    Product(int id) {

        this.id = id;
    }

    @Override
    public int hashCode() {

        return 1;
    }
}

public class HashCollisionExample {

    public static void main(String[] args) {

        Product productOne =
                new Product(101);

        Product productTwo =
                new Product(102);

        System.out.println(
                productOne.hashCode()
        );

        System.out.println(
                productTwo.hashCode()
        );
    }
}