// do-while runs the body once before checking the condition. Useful when the
// check depends on something produced by the body (typical example: prompt
// for input, validate it, re-prompt if invalid).

public class DoWhile {
    public static void main(String[] args) {

        // Stand-in for "ask the user for a value." We loop until we get a
        // non-empty string from somewhere. Real code would read from stdin or
        // a network — here we fake it with an index into a small array.
        String[] candidates = {"", "  ", "alice"};
        int i = 0;
        String value;

        do {
            value = candidates[i++];
            System.out.println("got: [" + value + "]");
        } while (value.trim().isEmpty());

        System.out.println("accepted: " + value);

        // Notice the body ran 3 times — twice with empty/blank input, once
        // with the valid "alice". With a plain while, you'd have to read once
        // before the loop and again inside it. do-while saves the duplication.
    }
}
