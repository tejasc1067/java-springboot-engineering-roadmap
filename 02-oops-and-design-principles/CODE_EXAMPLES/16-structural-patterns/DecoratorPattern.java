import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A decorator implements the same interface as the object it wraps, adds one behaviour,
// and passes the call on. Any PriceService can be wrapped, and decorators stack.
//
// COMPARE WITH: SubclassPerFeatureBroken.java

interface PriceService {
    int priceOf(String sku);
}

class HttpPriceService implements PriceService {
    int remoteCalls = 0;
    public int priceOf(String sku) { remoteCalls++; return 1999; }
}

class WarehousePriceService implements PriceService {
    int remoteCalls = 0;
    public int priceOf(String sku) { remoteCalls++; return 1999; }
}

class CachingPriceService implements PriceService {
    private final PriceService delegate;
    private final Map<String, Integer> cache = new HashMap<>();
    CachingPriceService(PriceService delegate) { this.delegate = delegate; }

    public int priceOf(String sku) { return cache.computeIfAbsent(sku, delegate::priceOf); }
}

class LoggingPriceService implements PriceService {
    private final PriceService delegate;
    final List<String> log = new ArrayList<>();
    LoggingPriceService(PriceService delegate) { this.delegate = delegate; }

    public int priceOf(String sku) {
        log.add("priceOf " + sku);
        return delegate.priceOf(sku);
    }
}

public class DecoratorPattern {
    public static void main(String[] args) {
        // The same caching class works for both sources.
        HttpPriceService http = new HttpPriceService();
        WarehousePriceService warehouse = new WarehousePriceService();
        PriceService cachedHttp = new CachingPriceService(http);
        PriceService cachedWarehouse = new CachingPriceService(warehouse);
        for (int i = 0; i < 3; i++) { cachedHttp.priceOf("SKU-1"); cachedWarehouse.priceOf("SKU-1"); }
        System.out.println("Http remote calls:      " + http.remoteCalls);
        System.out.println("Warehouse remote calls: " + warehouse.remoteCalls);
        if (http.remoteCalls != 1 || warehouse.remoteCalls != 1) throw new AssertionError("cache missed");

        // Decorators stack - and the ORDER changes the behaviour.
        LoggingPriceService outside = new LoggingPriceService(new CachingPriceService(new HttpPriceService()));
        for (int i = 0; i < 3; i++) outside.priceOf("SKU-1");

        LoggingPriceService inside = new LoggingPriceService(new HttpPriceService());
        PriceService cachedThenLogged = new CachingPriceService(inside);
        for (int i = 0; i < 3; i++) cachedThenLogged.priceOf("SKU-1");

        System.out.println("Logging(Caching(http)) logged " + outside.log.size() + " lookups (every request)");
        System.out.println("Caching(Logging(http)) logged " + inside.log.size() + " lookup  (only cache misses)");
        if (outside.log.size() != 3 || inside.log.size() != 1) throw new AssertionError("unexpected order effect");
    }
}
