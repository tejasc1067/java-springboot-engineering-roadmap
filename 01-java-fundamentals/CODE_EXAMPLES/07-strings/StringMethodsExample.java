public class StringMethodsExample {

    public static void main(String[] args) {

        String language = "Java Backend";

        System.out.println("Length: " + language.length());

        System.out.println("Uppercase: " + language.toUpperCase());

        System.out.println("Lowercase: " + language.toLowerCase());

        System.out.println("Character at Index 0: " + language.charAt(0));

        System.out.println("Contains Backend? " + language.contains("Backend"));
    }
}