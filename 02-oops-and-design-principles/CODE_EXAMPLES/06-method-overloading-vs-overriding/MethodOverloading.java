// Same method name, different parameter lists. The compiler picks one
// based on the argument types at the call site (compile time).

class Calculator {
    int add(int a, int b)             { return a + b; }
    double add(double a, double b)    { return a + b; }
    int add(int a, int b, int c)      { return a + b + c; }
    String add(String a, String b)    { return a + b; }
}

public class MethodOverloading {
    public static void main(String[] args) {
        Calculator c = new Calculator();
        System.out.println("int + int      = " + c.add(1, 2));
        System.out.println("double + double= " + c.add(1.5, 2.5));
        System.out.println("3 ints         = " + c.add(1, 2, 3));
        System.out.println("string + string= " + c.add("Hi ", "there"));

        // Note: returning different types alone does NOT make a valid overload.
        // See ReturnTypeAlone_Broken.java.
    }
}
