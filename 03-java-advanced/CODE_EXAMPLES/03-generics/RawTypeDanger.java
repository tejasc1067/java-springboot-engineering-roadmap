import java.util.ArrayList;
import java.util.List;

public class RawTypeDanger {

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) {
        List list = new ArrayList();
        list.add("hello");
        list.add(42);
        list.add("world");

        System.out.println("size: " + list.size());

        try {
            for (Object o : list) {
                String s = (String) o;
                System.out.println("got string: " + s);
            }
        } catch (ClassCastException e) {
            System.out.println("CRASH: " + e.getMessage());
            System.out.println("the cast at runtime was unsafe — compiler couldn't help");
        }
    }
}
