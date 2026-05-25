import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.util.Date;

public class OldVsNewDateApi {

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        System.out.println("=== problems with java.util.Date ===");

        // Trap 1: month is zero-indexed in the legacy constructor.
        // Date(year - 1900, month - 1, day) -- everything is offset.
        Date oldStyle = new Date(95, 6, 4);   // we MEANT July 4, 1995. The 6 is correct (July).
        System.out.println("new Date(95, 6, 4)  -- meant to be 1995-07-04, prints: " + oldStyle);

        // Trap 2: Date is mutable. Anyone with a reference can change it.
        Date appointment = new Date();
        Date copy = appointment;
        copy.setTime(0);                  // a bystander accidentally moves it to 1970
        System.out.println("after copy.setTime(0): " + appointment);

        // Trap 3: SimpleDateFormat is NOT thread-safe -- a classic production bug source.
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        System.out.println("SimpleDateFormat is NOT thread-safe -- sharing it across threads corrupts state.");

        System.out.println();
        System.out.println("=== java.time fixes all of this ===");

        // Immutable. Months are real names.
        LocalDate newStyle = LocalDate.of(1995, Month.JULY, 4);
        System.out.println("LocalDate.of(1995, JULY, 4) = " + newStyle);

        LocalDate plusOneYear = newStyle.plusYears(1);    // returns NEW object; original unchanged
        System.out.println("plusOneYear:           " + plusOneYear);
        System.out.println("original unchanged:    " + newStyle);

        System.out.println();
        System.out.println("DateTimeFormatter (java.time) IS thread-safe -- shared formatters are fine.");
    }
}
