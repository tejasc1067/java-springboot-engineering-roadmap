class AccessExample {

    private int privateValue = 10;

    int defaultValue = 20;

    protected int protectedValue = 30;

    public int publicValue = 40;

    void displayValues() {

        System.out.println("Private: " + privateValue);

        System.out.println("Default: " + defaultValue);

        System.out.println("Protected: " + protectedValue);

        System.out.println("Public: " + publicValue);
    }
}

public class AccessModifierComparisonExample {

    public static void main(String[] args) {

        AccessExample example = new AccessExample();

        example.displayValues();
    }
}