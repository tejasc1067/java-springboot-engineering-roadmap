import java.util.function.Supplier;

public class SupplierExample {

    public static void main(String[] args) {

        Supplier<String> configLoader =
                () -> "Database Configuration Loaded";

        System.out.println(
                configLoader.get()
        );
    }
}