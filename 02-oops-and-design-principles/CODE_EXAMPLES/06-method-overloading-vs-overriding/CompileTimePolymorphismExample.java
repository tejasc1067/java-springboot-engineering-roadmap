class Printer {

    void print(String text) {

        System.out.println(
                "Printing Text: " + text
        );
    }

    void print(int number) {

        System.out.println(
                "Printing Number: " + number
        );
    }
}

public class CompileTimePolymorphismExample {

    public static void main(String[] args) {

        Printer printer = new Printer();

        printer.print("Hello");

        printer.print(100);
    }
}