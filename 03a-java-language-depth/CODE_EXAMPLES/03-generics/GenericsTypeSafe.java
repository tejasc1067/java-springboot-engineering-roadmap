import java.util.ArrayList;
import java.util.List;

public class GenericsTypeSafe {

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("hello");
        // list.add(42);                <-- compile error, caught at write time
        list.add("world");

        for (String s : list) {
            System.out.println("got string: " + s);
        }

        System.out.println();
        System.out.println("no casts, no runtime surprises -- the compiler proved this safe.");
    }
}
