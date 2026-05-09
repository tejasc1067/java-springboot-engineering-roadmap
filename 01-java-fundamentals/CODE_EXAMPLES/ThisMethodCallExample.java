public class ThisMethodCallExample {

    void displayMessage() {

        System.out.println("Learning this keyword");
    }

    void showMessage() {

        // Calling current object method
        this.displayMessage();
    }

    public static void main(String[] args) {

        ThisMethodCallExample example = new ThisMethodCallExample();

        example.showMessage();
    }
}