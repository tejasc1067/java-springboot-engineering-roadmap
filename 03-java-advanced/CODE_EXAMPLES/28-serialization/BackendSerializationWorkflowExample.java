import java.io.*;
import java.io.Serializable;

class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private int orderId;

    public Order(int orderId) {

        this.orderId = orderId;
    }

    @Override
    public String toString() {

        return "Order ID: " + orderId;
    }
}

public class BackendSerializationWorkflowExample {

    public static void main(String[] args)
            throws Exception {

        Order order =
                new Order(101);

        ObjectOutputStream outputStream =
                new ObjectOutputStream(
                        new FileOutputStream("order.ser")
                );

        outputStream.writeObject(order);

        outputStream.close();

        ObjectInputStream inputStream =
                new ObjectInputStream(
                        new FileInputStream("order.ser")
                );

        Order restoredOrder =
                (Order) inputStream.readObject();

        inputStream.close();

        System.out.println(restoredOrder);
    }
}