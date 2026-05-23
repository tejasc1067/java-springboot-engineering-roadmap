// A pictorial demo: locals and method frames live on the stack, objects on
// the heap. When a method returns, its stack frame vanishes; the objects it
// created remain on the heap until nothing references them.

public class StackVsHeapAgain {

    public static void main(String[] args) {

        // Local primitive — STACK. Local reference — STACK. The Person — HEAP.
        Person p1 = new Person("alice", 30);
        System.out.println("main has p1 = " + p1);

        // Pass to another method.
        Person modified = aged(p1);
        System.out.println("aged returned " + modified);
        System.out.println("p1 is still   " + p1 + "   (we returned a new object)");

        // Mutate through the reference — both names see the change.
        modify(p1);
        System.out.println("after modify(p1): " + p1);

        // p1 = null;  // would make the alice Person eligible for GC
    }

    // Returns a new Person; doesn't change the original.
    static Person aged(Person p) {
        return new Person(p.name, p.age + 1);
    }

    // Mutates the Person the caller passed in.
    static void modify(Person p) {
        p.age = 99;
    }
}

class Person {
    String name;
    int age;
    Person(String name, int age) { this.name = name; this.age = age; }
    @Override public String toString() { return name + "(" + age + ")"; }
}
