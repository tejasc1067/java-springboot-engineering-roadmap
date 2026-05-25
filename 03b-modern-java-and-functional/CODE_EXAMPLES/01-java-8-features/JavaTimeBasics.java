import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class JavaTimeBasics {

    public static void main(String[] args) {
        // 1) LocalDate -- a date with no time and no zone. Good for birthdays.
        LocalDate today = LocalDate.now();
        LocalDate birthday = LocalDate.of(1995, 7, 4);
        Period age = Period.between(birthday, today);
        System.out.println("today:    " + today);
        System.out.println("birthday: " + birthday);
        System.out.println("age:      " + age.getYears() + " years, " + age.getMonths() + " months");

        System.out.println();

        // 2) Instant -- a point on the timeline (UTC). What you store in the DB.
        Instant now = Instant.now();
        Instant inFiveMinutes = now.plus(Duration.ofMinutes(5));
        System.out.println("now:                  " + now);
        System.out.println("now + 5min:           " + inFiveMinutes);
        System.out.println("duration between:     " + Duration.between(now, inFiveMinutes));

        System.out.println();

        // 3) ZonedDateTime -- Instant with a zone for human display.
        ZonedDateTime nowInTokyo = now.atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime nowInNewYork = now.atZone(ZoneId.of("America/New_York"));
        System.out.println("now in Tokyo:    " + nowInTokyo);
        System.out.println("now in NewYork:  " + nowInNewYork);

        System.out.println();

        // 4) Parsing and formatting.
        LocalDate parsed = LocalDate.parse("2026-12-25");
        System.out.println("parsed:    " + parsed);
        System.out.println("formatted: " + parsed.format(DateTimeFormatter.ofPattern("d MMMM yyyy")));
    }
}
