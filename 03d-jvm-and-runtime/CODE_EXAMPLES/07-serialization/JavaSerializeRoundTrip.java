import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class JavaSerializeRoundTrip {

    static class Customer implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int age;
        public Customer(String name, int age) { this.name = name; this.age = age; }
        public String toString() { return "Customer(" + name + ", " + age + ")"; }
    }

    public static void main(String[] args) throws Exception {
        Customer original = new Customer("Alice", 25);

        // Serialize to an in-memory byte[].
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        System.out.println("serialized size: " + bytes.size() + " bytes");

        // Deserialize back to a new object.
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Customer restored = (Customer) in.readObject();
            System.out.println("original: " + original);
            System.out.println("restored: " + restored);
            System.out.println("same instance? " + (original == restored));
        }
    }
}
