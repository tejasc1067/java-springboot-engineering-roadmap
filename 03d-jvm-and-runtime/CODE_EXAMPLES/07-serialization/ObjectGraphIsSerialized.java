import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ObjectGraphIsSerialized {

    static class Address implements Serializable {
        private static final long serialVersionUID = 1L;
        String city;
        Address(String city) { this.city = city; }
        public String toString() { return "Address(" + city + ")"; }
    }

    static class Customer implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        Address address;             // reference -- the whole graph is followed
        Customer friend;             // could be another Customer

        Customer(String name, Address address) { this.name = name; this.address = address; }
        public String toString() {
            return "Customer(" + name + ", " + address + ", friend=" + (friend == null ? "null" : friend.name) + ")";
        }
    }

    public static void main(String[] args) throws Exception {
        Customer alice = new Customer("Alice", new Address("Helsinki"));
        Customer bob   = new Customer("Bob",   new Address("Tampere"));
        alice.friend = bob;
        bob.friend = alice;           // cyclic reference -- Java handles this

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(alice);
        }
        System.out.println("serialized graph size: " + bytes.size() + " bytes");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Customer restored = (Customer) in.readObject();
            System.out.println("restored: " + restored);
            System.out.println("restored.friend: " + restored.friend);
            System.out.println("cycle preserved? " + (restored.friend.friend == restored));
        }
    }
}
