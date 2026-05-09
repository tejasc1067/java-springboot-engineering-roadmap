import java.io.IOException;

public class ThrowsKeywordExample {

    public static void processFile() throws IOException {

        throw new IOException("File Processing Failed");
    }

    public static void main(String[] args) {

        try {

            processFile();

        } catch (IOException exception) {

            System.out.println(exception.getMessage());
        }
    }
}