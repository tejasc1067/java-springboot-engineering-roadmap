import java.util.Scanner;

public class ImportExample {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter Your Name:");

        String name = scanner.nextLine();

        System.out.println("Welcome " + name);

        scanner.close();
    }
}