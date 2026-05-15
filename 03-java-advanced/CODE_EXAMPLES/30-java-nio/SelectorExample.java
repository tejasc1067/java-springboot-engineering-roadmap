import java.nio.channels.Selector;

public class SelectorExample {

    public static void main(String[] args)
            throws Exception {

        Selector selector =
                Selector.open();

        System.out.println(
                "Selector Created: "
                        + selector.isOpen()
        );

        selector.close();
    }
}