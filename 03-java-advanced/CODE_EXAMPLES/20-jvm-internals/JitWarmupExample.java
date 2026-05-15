public class JitWarmupExample {

    public static void main(String[] args) {

        long startTime =
                System.nanoTime();

        for (int index = 0;
             index < 1000000;
             index++) {

            calculate();
        }

        long endTime =
                System.nanoTime();

        System.out.println(
                "Execution Time: "
                        + (endTime - startTime)
        );
    }

    private static int calculate() {

        return 10 + 20;
    }
}