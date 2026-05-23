// Static import brings a class's static members into scope, so you can use
// them WITHOUT the class name prefix.
//
// Useful in tests (assertEquals, assertTrue) and in math-heavy code. Risky
// elsewhere — the reader has to know that `sqrt` came from Math.

import static java.lang.Math.PI;
import static java.lang.Math.sqrt;
import static java.lang.Math.pow;

public class StaticImport {
    public static void main(String[] args) {

        double radius = 5;
        double area = PI * pow(radius, 2);
        double diagonal = sqrt(pow(3, 2) + pow(4, 2));

        System.out.println("area of circle (r=5) = " + area);
        System.out.println("diagonal of 3x4 box  = " + diagonal);

        // Without the static imports, this would have been:
        //   double area = Math.PI * Math.pow(radius, 2);
        //   double diagonal = Math.sqrt(Math.pow(3, 2) + Math.pow(4, 2));
        //
        // In a math-heavy block, the static-imported version reads cleaner.
        // In general application code, prefer Math.sqrt — the prefix tells
        // readers where the function came from.
    }
}
