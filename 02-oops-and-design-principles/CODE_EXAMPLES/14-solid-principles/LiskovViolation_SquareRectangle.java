// The textbook LSP violation. Geometrically a square IS a rectangle.
// In code, this inheritance breaks substitutability.
//
// A method written against Rectangle and tested with Rectangles fails when
// passed a Square — because Square's overrides contradict Rectangle's contract.

class Rectangle {
    protected int width, height;

    void setWidth(int w)  { this.width = w; }
    void setHeight(int h) { this.height = h; }
    int area() { return width * height; }
}

class Square extends Rectangle {
    @Override
    void setWidth(int w) {
        this.width = w;
        this.height = w;     // forced equality — contradicts Rectangle's "set width independently" contract
    }
    @Override
    void setHeight(int h) {
        this.width = h;
        this.height = h;
    }
}

public class LiskovViolation_SquareRectangle {

    // Method written against Rectangle — should work for ANY Rectangle subtype.
    static void resizeAndCheck(Rectangle r) {
        r.setWidth(5);
        r.setHeight(4);
        boolean ok = (r.area() == 20);
        System.out.println("After setWidth(5), setHeight(4): area = " + r.area() + (ok ? " (ok)" : " (BROKEN)"));
    }

    public static void main(String[] args) {
        resizeAndCheck(new Rectangle());    // 5 * 4 = 20 — ok
        resizeAndCheck(new Square());        // setHeight resized BOTH; area = 16 — broken

        System.out.println("\nA function that works on Rectangle doesn't work on Square.");
        System.out.println("Square 'is a' Rectangle mathematically, but the inheritance contract fails.");
        System.out.println("Lesson: subtypes must respect the parent's behavioral contract, not just its method names.");
    }
}
