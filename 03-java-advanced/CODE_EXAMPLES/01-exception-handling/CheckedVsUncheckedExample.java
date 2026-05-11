import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CheckedVsUncheckedExample {

    public static void main(String[] args) {

        // Checked Exception

        try {

            BufferedReader reader =
                    new BufferedReader(
                            new FileReader(
                                    "sample.txt"
                            )
                    );

            reader.close();

        } catch (IOException exception) {

            System.out.println(
                    "Checked Exception Handled"
            );
        }

        // Unchecked Exception

        try {

            int result =
                    10 / 0;

            System.out.println(result);

        } catch (ArithmeticException exception) {

            System.out.println(
                    "Unchecked Exception Handled"
            );
        }
    }
}