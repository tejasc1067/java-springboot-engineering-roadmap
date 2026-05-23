// All four access levels in one file. The default-package single-file launch
// means we can only show "same class" and "same package" boundaries here —
// cross-package access requires a real project layout (covered in topic 11).
//
// The forbidden access is commented out. Uncomment any line to see the
// compile error.

public class AccessLevels {
    public static void main(String[] args) {

        Box b = new Box();
        b.self();

        // Same package — public, protected, and package-private are visible.
        System.out.println("\nfrom AccessLevels (same package):");
        System.out.println("  publicField    = " + b.publicField);
        System.out.println("  protectedField = " + b.protectedField);
        System.out.println("  packageField   = " + b.packageField);

        // privateField is NOT accessible here. Uncomment to see the error:
        //   System.out.println("  privateField   = " + b.privateField);
        //   --> error: privateField has private access in Box
    }
}

class Box {
    public    int publicField    = 1;
    protected int protectedField = 2;
              int packageField   = 3;     // no keyword = package-private
    private   int privateField   = 4;

    void self() {
        // Same class: all four are visible.
        System.out.println("inside Box: "
                + publicField + " " + protectedField + " "
                + packageField + " " + privateField);
    }
}
