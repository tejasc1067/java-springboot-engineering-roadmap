public class ClassForNameVsLoadClass {

    static class Loud {
        static { System.out.println("Loud.<clinit> ran"); }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader cl = ClassForNameVsLoadClass.class.getClassLoader();
        String name = "ClassForNameVsLoadClass$Loud";

        System.out.println("calling loadClass(...) -- loads, links, but does NOT initialize");
        Class<?> c1 = cl.loadClass(name);
        System.out.println("loaded: " + c1.getName());

        System.out.println();
        System.out.println("calling Class.forName(...) -- ALSO initializes (runs <clinit>)");
        Class<?> c2 = Class.forName(name);
        System.out.println("forName returned: " + c2.getName());

        System.out.println();
        System.out.println("Calling Class.forName again -- <clinit> already ran, no second print");
        Class.forName(name);
    }
}
