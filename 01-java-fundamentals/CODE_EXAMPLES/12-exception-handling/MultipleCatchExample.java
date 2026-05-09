public class MultipleCatchExample {

    public static void main(String[] args) {

        try {

            String value = null;

            System.out.println(value.length());

        } catch (NullPointerException exception) {

            System.out.println("Null Value Found");

        } catch (Exception exception) {

            System.out.println("General Exception Occurred");
        }
    }
}