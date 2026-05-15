interface Greeting {

    void sayHello(String name);
}

public class LambdaBasicsExample {

    public static void main(String[] args) {

        Greeting greeting =
                (name) -> {

                    System.out.println(
                            "Hello " + name
                    );
                };

        greeting.sayHello("Tejas");
    }
}