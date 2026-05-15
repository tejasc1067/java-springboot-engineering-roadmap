public class StringBuilderOptimizationExample {

    public static void main(String[] args) {

        StringBuilder builder =
                new StringBuilder();

        for (int index = 0;
             index < 5;
             index++) {

            builder.append(index)
                    .append(" ");
        }

        System.out.println(
                builder.toString()
        );
    }
}