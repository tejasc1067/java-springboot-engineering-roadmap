public class NestedLoopExample {

    public static void main(String[] args) {

        // Generating seat rows and seat numbers
        for (int row = 1; row <= 3; row++) {

            for (int seat = 1; seat <= 2; seat++) {

                System.out.println("Row " + row + " Seat " + seat);
            }
        }
    }
}