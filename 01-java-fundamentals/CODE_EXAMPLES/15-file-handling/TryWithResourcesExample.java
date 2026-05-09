import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TryWithResourcesExample {

    public static void main(String[] args) {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader("sample.txt")
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                System.out.println(line);
            }

        } catch (IOException exception) {

            System.out.println(exception.getMessage());
        }
    }
}