import java.sql.Driver;

public class ClassLoaderHierarchy {

    public static void main(String[] args) {
        System.out.println("Application class (this one):");
        printChain(ClassLoaderHierarchy.class);

        System.out.println();
        System.out.println("Platform-loaded class (java.sql.Driver):");
        printChain(Driver.class);

        System.out.println();
        System.out.println("Bootstrap-loaded class (java.lang.String):");
        printChain(String.class);
    }

    static void printChain(Class<?> c) {
        System.out.println("  " + c.getName());
        ClassLoader cl = c.getClassLoader();
        int depth = 1;
        while (cl != null) {
            System.out.println("    " + indent(depth) + cl.getClass().getName() + "  ->  name=" + cl.getName());
            cl = cl.getParent();
            depth++;
        }
        System.out.println("    " + indent(depth) + "(bootstrap, represented as null)");
    }

    static String indent(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append("  ");
        return s.toString();
    }
}
