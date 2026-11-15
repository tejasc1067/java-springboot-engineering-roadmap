import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// A decorator has to re-implement every method of the interface by hand.
// A dynamic proxy is a class the JVM generates at runtime: every method call on it
// goes to ONE InvocationHandler. This is how Spring adds @Transactional, @Cacheable
// and your own @Aspect advice to beans without touching their code (module 07, topic 10).

interface AccountService {
    void deposit(String account, long cents);
    void withdraw(String account, long cents);
    long balance(String account);
}

class SimpleAccountService implements AccountService {
    private long balance = 0;
    public void deposit(String account, long cents) { balance += cents; }
    public void withdraw(String account, long cents) { balance -= cents; }
    public long balance(String account) { return balance; }
}

public class DynamicProxy {
    static final List<String> audit = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static <T> T audited(T target, Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            audit.add("before " + method.getName());
            Object result = method.invoke(target, args); // call the real object
            audit.add("after  " + method.getName());
            return result;
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    public static void main(String[] args) {
        AccountService real = new SimpleAccountService();
        AccountService service = audited(real, AccountService.class);

        service.deposit("acc-1", 5000);
        service.withdraw("acc-1", 1200);
        long balance = service.balance("acc-1");

        audit.forEach(System.out::println);
        System.out.println("balance = " + balance);
        System.out.println("proxy class: " + service.getClass().getName() + " (generated at runtime)");

        if (balance != 3800) throw new AssertionError("balance " + balance);
        if (audit.size() != 6) throw new AssertionError("every method should be audited: " + audit);
    }
}
