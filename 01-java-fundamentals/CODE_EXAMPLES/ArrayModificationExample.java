public class ArrayModificationExample {

    public static void main(String[] args) {

        int[] marks = {90, 85, 80};

        // Modifying array value
        marks[1] = 88;

        for (int mark : marks) {

            System.out.println(mark);
        }
    }
}