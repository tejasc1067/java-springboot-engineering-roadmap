import java.util.ArrayList;
import java.util.List;

public class TypeErasureProof {

    public static void main(String[] args) {
        List<String>  strings = new ArrayList<>();
        List<Integer> ints    = new ArrayList<>();

        System.out.println("strings.getClass() = " + strings.getClass().getName());
        System.out.println("ints.getClass()    = " + ints.getClass().getName());
        System.out.println("same class?        = " + (strings.getClass() == ints.getClass()));
        System.out.println();
        System.out.println("at runtime, both are just ArrayList -- <String> and <Integer> erased.");
        System.out.println();

        Object opaque = strings;
        if (opaque instanceof List<?>) {
            System.out.println("we can check `instanceof List<?>` (the unbounded wildcard)");
            System.out.println("but `instanceof List<String>` won't compile -- no runtime type info");
        }
    }
}
