public class SqlInjectionSimulation {

    public static void main(String[] args) {

        String maliciousInput =
                "' OR '1'='1";

        System.out.println(
                "Injected Input:"
        );

        System.out.println(maliciousInput);

        System.out.println(
                "Authentication Bypass Attempt"
        );
    }
}