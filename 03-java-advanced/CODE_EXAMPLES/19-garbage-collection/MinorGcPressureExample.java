public class MinorGcPressureExample {

    public static void main(String[] args) {

        for (int index = 0;
             index < 10000;
             index++) {

            byte[] data =
                    new byte[1024];

            System.out.println(
                    "Temporary Object Created"
            );
        }
    }
}