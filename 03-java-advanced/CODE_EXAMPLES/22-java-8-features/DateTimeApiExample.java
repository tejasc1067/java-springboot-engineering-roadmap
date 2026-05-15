import java.time.LocalDate;
import java.time.LocalDateTime;

public class DateTimeApiExample {

    public static void main(String[] args) {

        LocalDate currentDate =
                LocalDate.now();

        LocalDateTime currentDateTime =
                LocalDateTime.now();

        System.out.println(
                currentDate
        );

        System.out.println(
                currentDateTime
        );
    }
}