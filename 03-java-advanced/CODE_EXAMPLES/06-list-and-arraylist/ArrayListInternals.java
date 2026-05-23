import java.util.ArrayList;

public class ArrayListInternals {

    public static void main(String[] args) {
        ArrayList<Integer> list = new ArrayList<>();
        System.out.println("freshly created -- size=" + list.size());

        for (int i = 1; i <= 13; i++) {
            list.add(i);
            System.out.println("after add " + i + ": size=" + list.size());
        }

        System.out.println();
        System.out.println("default initial capacity is 10. Adding the 11th element triggers a");
        System.out.println("grow to ~15 (1.5x). You can't query capacity directly -- it's internal.");
        System.out.println();

        list.add(7, 999);
        System.out.println("after add(7, 999): " + list);
        System.out.println("  inserting at the middle shifts elements 7..N-1 right by one (O(n)).");

        list.remove(0);
        System.out.println("after remove(0):   " + list);
        System.out.println("  removing at the start shifts everything left by one (O(n)).");
    }
}
