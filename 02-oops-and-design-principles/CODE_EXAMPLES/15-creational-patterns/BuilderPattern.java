// The same UserAccount built with a builder: every value is set by name, required
// values are enforced, and the finished object is immutable (topic 13).
//
// COMPARE WITH: TelescopingConstructorBroken.java

final class UserAccount {
    private final String email;
    private final String displayName;
    private final boolean admin;
    private final boolean emailVerified;
    private final boolean locked;

    // private: the only way to get a UserAccount is through the builder
    private UserAccount(Builder b) {
        this.email = b.email;
        this.displayName = b.displayName;
        this.admin = b.admin;
        this.emailVerified = b.emailVerified;
        this.locked = b.locked;
    }

    // Required values go in the builder() call, so they can't be forgotten.
    static Builder builder(String email) {
        return new Builder(email);
    }

    boolean isAdmin() { return admin; }
    boolean isEmailVerified() { return emailVerified; }
    String displayName() { return displayName; }

    static final class Builder {
        private final String email;
        private String displayName;
        private boolean admin;           // optional values default to the safe choice
        private boolean emailVerified;
        private boolean locked;

        private Builder(String email) { this.email = email; }

        Builder displayName(String displayName) { this.displayName = displayName; return this; }
        Builder admin(boolean admin) { this.admin = admin; return this; }
        Builder emailVerified(boolean verified) { this.emailVerified = verified; return this; }
        Builder locked(boolean locked) { this.locked = locked; return this; }

        // All rules checked once, before an object exists.
        UserAccount build() {
            if (email == null || !email.contains("@")) {
                throw new IllegalStateException("email is required and must contain @");
            }
            if (displayName == null) {
                displayName = email.substring(0, email.indexOf('@'));
            }
            return new UserAccount(this);
        }
    }
}

public class BuilderPattern {
    public static void main(String[] args) {
        UserAccount bob = UserAccount.builder("bob@example.com")
                .displayName("Bob")
                .emailVerified(true)
                .build();

        System.out.println("bob.emailVerified = " + bob.isEmailVerified());
        System.out.println("bob.admin         = " + bob.isAdmin());
        if (bob.isAdmin() || !bob.isEmailVerified()) throw new AssertionError("wrong flags");

        UserAccount alice = UserAccount.builder("alice@example.com").build();
        System.out.println("alice.displayName = " + alice.displayName() + "  (default filled in by build())");

        try {
            UserAccount.builder("not-an-email").build();
        } catch (IllegalStateException e) {
            System.out.println("Invalid input rejected: " + e.getMessage());
        }
    }
}
