import java.util.ArrayList;
import java.util.List;

public class RemoveOverloadTrap {

    public static void main(String[] args) {
        List<Integer> nums = new ArrayList<>(List.of(10, 20, 30, 40, 50));
        System.out.println("starting: " + nums);

        nums.remove(2);
        System.out.println("after remove(2):                       " + nums + "   <-- removed index 2 (the value 30)");

        List<Integer> nums2 = new ArrayList<>(List.of(10, 20, 30, 40, 50));
        nums2.remove(Integer.valueOf(20));
        System.out.println("after remove(Integer.valueOf(20)):     " + nums2 + "   <-- removed the value 20");

        System.out.println();
        System.out.println("trap: on List<Integer>, remove(int) is index-based -- easy to think you're");
        System.out.println("      asking for value removal. Always wrap with Integer.valueOf(...) when");
        System.out.println("      you want value removal.");
    }
}
