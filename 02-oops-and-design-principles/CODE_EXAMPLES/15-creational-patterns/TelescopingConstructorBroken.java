// Five constructor parameters, three of them booleans. The compiler checks the types,
// not the meaning - so swapping two booleans compiles fine.
//
// COMPARE WITH: BuilderPattern.java

final class UserAccount {
    final String email;
    final String displayName;
    final boolean admin;
    final boolean emailVerified;
    final boolean locked;

    UserAccount(String email, String displayName, boolean admin, boolean emailVerified, boolean locked) {
        this.email = email;
        this.displayName = displayName;
        this.admin = admin;
        this.emailVerified = emailVerified;
        this.locked = locked;
    }
}

public class TelescopingConstructorBroken {
    public static void main(String[] args) {
        // Intended: a normal user whose email is verified.
        // The author remembered the order as (email, name, emailVerified, admin, locked).
        UserAccount bob = new UserAccount("bob@example.com", "Bob", true, false, false);

        System.out.println("bob.emailVerified = " + bob.emailVerified);
        System.out.println("bob.admin         = " + bob.admin);
        System.out.println("A sign-up form just created an administrator. Nothing failed.");
    }
}
