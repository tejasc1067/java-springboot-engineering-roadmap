// Static methods belong to the class, not to an object. There IS no "current
// object" — so `this` is a compile error.
//
// The broken code is commented out so the file compiles. Uncomment the line
// inside reset() and re-run to see the error:
//
//   error: non-static variable this cannot be referenced from a static context

public class ThisStaticError {
    public static void main(String[] args) {

        Counter c = new Counter();
        c.increment();
        c.increment();
        System.out.println("count = " + c.count);

        Counter.reset();   // called on the CLASS, not on c
    }
}

class Counter {
    int count;

    void increment() {
        this.count++;        // OK — instance method has a `this`
    }

    static void reset() {
        // this.count = 0;   // <- uncomment to see the compile error
        System.out.println("reset() can't access `this` — no current object");
    }
}
