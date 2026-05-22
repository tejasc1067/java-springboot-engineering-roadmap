// final on a class blocks all subclassing. Used for value types like String,
// Integer, LocalDate where consistent behavior across the codebase matters.

final class ImmutablePoint {
    final int x, y;
    ImmutablePoint(int x, int y) { this.x = x; this.y = y; }
}

// class SneakySubclass extends ImmutablePoint {   // ← would not compile
//     ImmutablePoint(int x, int y) { super(x, y); }
// }

public class FinalClassCannotExtend {
    public static void main(String[] args) {
        ImmutablePoint p = new ImmutablePoint(3, 4);
        System.out.println("p = (" + p.x + ", " + p.y + ")");
        System.out.println("ImmutablePoint is final — nothing can subclass it and change its behavior.");
        System.out.println("This is exactly why String, Integer, LocalDate are all declared final.");
    }
}
