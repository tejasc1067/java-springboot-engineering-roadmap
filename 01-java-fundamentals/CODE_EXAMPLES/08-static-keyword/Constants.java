// The constant idiom: public static final.
//   - public: anyone can read
//   - static: one shared copy
//   - final: never reassigned
// Convention: SCREAMING_SNAKE_CASE names.

public class Constants {
    public static void main(String[] args) {

        System.out.println("OK         = " + HttpStatus.OK);
        System.out.println("NOT_FOUND  = " + HttpStatus.NOT_FOUND);

        // In real code:
        int responseCode = 404;
        if (responseCode == HttpStatus.NOT_FOUND) {
            System.out.println("\nresource missing");
        }

        // Without `final`, someone could write `HttpStatus.OK = 999;` and
        // wreck every comparison everywhere. `final` is what makes the constant
        // a guarantee instead of just a default.
    }
}

class HttpStatus {
    public static final int OK             = 200;
    public static final int CREATED        = 201;
    public static final int BAD_REQUEST    = 400;
    public static final int NOT_FOUND      = 404;
    public static final int INTERNAL_ERROR = 500;

    private HttpStatus() {            // prevent anyone from instantiating
        throw new AssertionError("constants holder — do not instantiate");
    }
}
