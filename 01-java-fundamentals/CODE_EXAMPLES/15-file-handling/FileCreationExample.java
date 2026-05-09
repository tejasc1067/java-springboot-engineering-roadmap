import java.io.File;
import java.io.IOException;

public class FileCreationExample {

    public static void main(String[] args) {

        File file = new File("sample.txt");

        try {

            if (file.createNewFile()) {

                System.out.println("File Created Successfully");

            } else {

                System.out.println("File Already Exists");
            }

        } catch (IOException exception) {

            System.out.println(exception.getMessage());
        }
    }
}