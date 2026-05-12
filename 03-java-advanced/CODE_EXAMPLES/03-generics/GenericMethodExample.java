public class GenericMethodExample {

    static <T> void printData(T data) {

        System.out.println(data);
    }

    public static void main(String[] args) {

        printData("Tejas");

        printData(101);

        printData(99.99);
    }
}