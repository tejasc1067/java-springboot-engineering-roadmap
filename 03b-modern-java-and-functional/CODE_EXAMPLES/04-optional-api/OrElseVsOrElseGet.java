import java.util.Optional;

public class OrElseVsOrElseGet {

    static int defaultBuilds = 0;

    static String buildExpensiveDefault() {
        defaultBuilds++;
        return "DEFAULT";
    }

    public static void main(String[] args) {
        Optional<String> present = Optional.of("hello");

        // orElse(arg) -- arg is evaluated EAGERLY, before the call, even though it isn't needed.
        defaultBuilds = 0;
        String r1 = present.orElse(buildExpensiveDefault());
        System.out.println("orElse with present value:    result='" + r1 + "', builds=" + defaultBuilds);

        // orElseGet(supplier) -- supplier only runs when the Optional is empty.
        defaultBuilds = 0;
        String r2 = present.orElseGet(OrElseVsOrElseGet::buildExpensiveDefault);
        System.out.println("orElseGet with present value: result='" + r2 + "', builds=" + defaultBuilds);

        Optional<String> absent = Optional.empty();

        defaultBuilds = 0;
        String r3 = absent.orElse(buildExpensiveDefault());
        System.out.println("orElse with empty:            result='" + r3 + "', builds=" + defaultBuilds);

        defaultBuilds = 0;
        String r4 = absent.orElseGet(OrElseVsOrElseGet::buildExpensiveDefault);
        System.out.println("orElseGet with empty:         result='" + r4 + "', builds=" + defaultBuilds);

        System.out.println();
        System.out.println("Lesson: if the default is expensive, use orElseGet so it only runs when needed.");
    }
}
