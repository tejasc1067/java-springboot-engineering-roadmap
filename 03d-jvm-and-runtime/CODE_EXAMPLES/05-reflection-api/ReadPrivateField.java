import java.lang.reflect.Field;

// Top-level class, separate from ReadPrivateField. That matters: a class can
// reach private members of its OWN nested classes without setAccessible
// (the compiler emits synthetic accessors). Across unrelated classes, the
// access check is what setAccessible(true) bypasses.
class Secret {
    @SuppressWarnings("unused")
    private String password = "s3cr3t";
    public String publicName = "alice";
}

public class ReadPrivateField {

    public static void main(String[] args) throws Exception {
        Secret s = new Secret();

        // Public field -- accessible without setAccessible.
        Field pub = Secret.class.getDeclaredField("publicName");
        System.out.println("publicName = " + pub.get(s));

        // Private field, attempted from an unrelated class.
        Field priv = Secret.class.getDeclaredField("password");
        try {
            priv.get(s);
            System.out.println("(unexpectedly succeeded -- check whether Secret is in the same access context)");
        } catch (IllegalAccessException e) {
            System.out.println("without setAccessible: IllegalAccessException -- private field is locked from outside");
        }

        priv.setAccessible(true);                  // <-- bypass private
        System.out.println("password   = " + priv.get(s));

        // Reflection can also write the field.
        priv.set(s, "new-password");
        System.out.println("after set: " + priv.get(s));
    }
}
