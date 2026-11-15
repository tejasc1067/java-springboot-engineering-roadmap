import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// Fix: move the inner method to a separate object, and give OrderServiceImpl the PROXY
// of that object. Now the call crosses an object boundary and goes through a proxy.
// In Spring this is "move it to another @Service and inject that bean".
//
// COMPARE WITH: ProxySelfInvocationBroken.java

interface AuditService {
    void saveAuditRecord(String id);
}

class AuditServiceImpl implements AuditService {
    public void saveAuditRecord(String id) { }
}

interface OrderService {
    void placeOrder(String id);
}

class OrderServiceImpl implements OrderService {
    private final AuditService audit;
    OrderServiceImpl(AuditService audit) { this.audit = audit; }

    public void placeOrder(String id) {
        audit.saveAuditRecord(id); // a call on another object - the proxy sees it
    }
}

public class ProxySelfInvocationFixed {
    static final List<String> inTransaction = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static <T> T transactional(T target, Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            inTransaction.add(method.getName());
            return method.invoke(target, args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    public static void main(String[] args) {
        AuditService audit = transactional(new AuditServiceImpl(), AuditService.class);
        OrderService orders = transactional(new OrderServiceImpl(audit), OrderService.class);
        orders.placeOrder("order-7");

        System.out.println("Methods the proxy wrapped in a transaction: " + inTransaction);
        if (!inTransaction.equals(List.of("placeOrder", "saveAuditRecord"))) throw new AssertionError(inTransaction);
    }
}
