import java.io.Serializable;

class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    public Product(String name) {

        this.name = name;
    }

    @Override
    public String toString() {

        return name;
    }
}

public class SerialVersionUidExample {

    public static void main(String[] args) {

        Product product =
                new Product("Laptop");

        System.out.println(product);
    }
}