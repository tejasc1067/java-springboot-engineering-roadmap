// Return type alone is NOT enough to overload. Both methods below have the
// same name and parameter list — the compiler can't tell them apart at call
// sites, so it rejects the class.
//
// Uncomment the two foo() methods to see the error: "method foo() is already
// defined in class". The compiler doesn't look at return type when matching
// overloads.

public class ReturnTypeAlone_Broken {

    // int foo() {
    //     return 1;
    // }
    //
    // double foo() {            // ← will not compile alongside the int version
    //     return 1.0;
    // }

    public static void main(String[] args) {
        System.out.println("Uncomment the two foo() methods above to see the compile error.");
        System.out.println("Lesson: overloads must differ in parameter list, not just return type.");
    }
}
