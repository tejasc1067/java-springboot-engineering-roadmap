final class Utility {

    void displayMessage() {

        System.out.println(
                "Utility Class"
        );
    }
}

// Not Allowed
// class ChildUtility extends Utility {
// }

public class FinalClassExample {

    public static void main(String[] args) {

        Utility utility = new Utility();

        utility.displayMessage();
    }
}