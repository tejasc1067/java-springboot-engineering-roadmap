import java.util.HashMap;
import java.util.Map;

class Product {

    private final int id;

    Product(int id) {

        this.id = id;
    }

    @Override
    public int hashCode() {

        return 1;
    }

    @Override
    public boolean equals(Object object) {

        return false;
    }
}

public class CollisionHandlingExample {

    public static void main(String[] args) {

        Map<Product, String> products =
                new HashMap<>();

        products.put(
                new Product(101),
                "Laptop"
        );

        products.put(
                new Product(102),
                "Mobile"
        );

        System.out.println(products.size());
    }
}