import java.lang.ref.WeakReference;

class User {

    String name =
            "Tejas";
}

public class WeakReferenceExample {

    public static void main(String[] args) {

        User user =
                new User();

        WeakReference<User> weakReference =
                new WeakReference<>(user);

        System.out.println(
                weakReference.get()
        );

        user = null;

        System.gc();

        System.out.println(
                weakReference.get()
        );
    }
}