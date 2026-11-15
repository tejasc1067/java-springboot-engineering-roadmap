import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// A proxy only sees calls that go THROUGH it. When a method calls another method
// on `this`, the call goes straight to the real object and the proxy never knows.
// This is exactly why a Spring @Transactional or @Async method called from the same
// class silently does nothing.
//
// COMPARE WITH: ProxySelfInvocationFixed.java

interface OrderService {
    void placeOrder(String id);
    void saveAuditRecord(String id);   // should run inside a transaction
}

class OrderServiceImpl implements OrderService {
    public void placeOrder(String id) {
        saveAuditRecord(id); // really this.saveAuditRecord(id): `this` is the real object, not the proxy
    }
    public void saveAuditRecord(String id) { }
}

public class ProxySelfInvocationBroken {
    static final List<String> inTransaction = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static <T> T transactional(T target, Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            inTransaction.add(method.getName()); // stands in for "begin transaction ... commit"
            return method.invoke(target, args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    public static void main(String[] args) {
        OrderService orders = transactional(new OrderServiceImpl(), OrderService.class);
        orders.placeOrder("order-7");

        System.out.println("Methods the proxy wrapped in a transaction: " + inTransaction);
        if (inTransaction.contains("saveAuditRecord")) throw new AssertionError("expected the self-call to bypass the proxy");
        System.out.println("saveAuditRecord ran, but never passed through the proxy - no transaction.");
    }
}
