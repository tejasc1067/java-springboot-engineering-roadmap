import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class Product {

    private final String name;

    private final double price;

    Product(String name,
            double price) {

        this.name = name;

        this.price = price;
    }

    public String getName() {

        return name;
    }

    public double getPrice() {

        return price;
    }

    @Override
    public String toString() {

        return name + " - " + price;
    }
}

public class MultipleSortingExample {

    public static void main(String[] args) {

        List<Product> products =
                new ArrayList<>();

        products.add(
                new Product("Laptop", 80000)
        );

        products.add(
                new Product("Mobile", 40000)
        );

        products.add(
                new Product("Tablet", 30000)
        );

        products.sort(
                Comparator.comparing(
                        Product::getName
                )
        );

        System.out.println(products);

        products.sort(
                Comparator.comparing(
                        Product::getPrice
                )
        );

        System.out.println(products);
    }
}