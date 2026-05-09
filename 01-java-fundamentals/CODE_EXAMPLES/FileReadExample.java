import java.io.FileReader;
import java.io.IOException;

public class FileReadExample {

    public static void main(String[] args) {

        try {

            FileReader reader =
                    new FileReader("sample.txt");

            int character;

            while ((character = reader.read()) != -1) {

                System.out.print((char) character);
            }

            reader.close();

        } catch (IOException exception) {

            System.out.println(exception.getMessage());
        }
    }
}