public class PublicAccessExample {

    // Public method
    public void displayMessage() {

        System.out.println("Public Method Accessible Everywhere");
    }

    public static void main(String[] args) {

        PublicAccessExample example = new PublicAccessExample();

        example.displayMessage();
    }
}