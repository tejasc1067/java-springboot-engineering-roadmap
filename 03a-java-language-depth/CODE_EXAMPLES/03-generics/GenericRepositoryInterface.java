import java.util.HashMap;
import java.util.Map;

public class GenericRepositoryInterface {

    interface Repository<T, ID> {
        T findById(ID id);
        void save(ID id, T entity);
    }

    static class User {
        final String name;
        User(String name) { this.name = name; }
        @Override public String toString() { return "User(" + name + ")"; }
    }

    static class UserRepository implements Repository<User, Long> {
        private final Map<Long, User> store = new HashMap<>();
        public User findById(Long id)        { return store.get(id); }
        public void save(Long id, User u)    { store.put(id, u); }
    }

    static class Order {
        final double total;
        Order(double total) { this.total = total; }
        @Override public String toString() { return "Order($" + total + ")"; }
    }

    static class OrderRepository implements Repository<Order, String> {
        private final Map<String, Order> store = new HashMap<>();
        public Order findById(String id)        { return store.get(id); }
        public void save(String id, Order o)    { store.put(id, o); }
    }

    public static void main(String[] args) {
        UserRepository users = new UserRepository();
        users.save(1L, new User("Alice"));
        System.out.println("found user: " + users.findById(1L));

        OrderRepository orders = new OrderRepository();
        orders.save("ORD-100", new Order(199.99));
        System.out.println("found order: " + orders.findById("ORD-100"));

        System.out.println();
        System.out.println("same interface, two different (entity, id) shapes.");
    }
}
