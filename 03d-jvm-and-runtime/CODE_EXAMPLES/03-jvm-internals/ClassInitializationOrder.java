public class ClassInitializationOrder {

    static class A {
        static { System.out.println("A.<clinit> running"); }
        static int value = init("A.value");
        static int init(String label) { System.out.println("init " + label); return 1; }
    }

    static class B {
        static { System.out.println("B.<clinit> running -- triggered by reading A.value next"); }
        static int value = A.value + 1;       // reference to A triggers A's <clinit>
    }

    public static void main(String[] args) {
        System.out.println("main starts");
        System.out.println("---- first read of B.value ----");
        int x = B.value;
        System.out.println("B.value = " + x);

        System.out.println("---- second read of B.value ----");
        int y = B.value;          // no <clinit> this time: already initialized
        System.out.println("B.value = " + y);
    }
}
