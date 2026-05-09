import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class BufferedWriterExample {

    public static void main(String[] args) {

        try {

            BufferedWriter writer =
                    new BufferedWriter(
                            new FileWriter("sample.txt")
                    );

            writer.write("Buffered File Writing Example");

            writer.newLine();

            writer.write("Backend Engineering Learning");

            writer.close();

            System.out.println("Buffered Writing Completed");

        } catch (IOException exception) {

            System.out.println(exception.getMessage());
        }
    }
}