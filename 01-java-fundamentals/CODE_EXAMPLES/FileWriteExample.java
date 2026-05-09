import java.io.FileWriter;
import java.io.IOException;

public class FileWriteExample {

    public static void main(String[] args) {

        try {

            FileWriter writer =
                    new FileWriter("sample.txt");

            writer.write("Learning Java File Handling");

            writer.close();

            System.out.println("File Written Successfully");

        } catch (IOException exception) {

            System.out.println(exception.getMessage());
        }
    }
}