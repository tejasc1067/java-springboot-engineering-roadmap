import java.util.HashMap;
import java.util.Map;

// Adding caching by SUBCLASSING one implementation. It works - until you swap the implementation.
//
// COMPARE WITH: DecoratorPattern.java

interface PriceService {
    int priceOf(String sku);
}

class HttpPriceService implements PriceService {
    int remoteCalls = 0;
    public int priceOf(String sku) { remoteCalls++; return 1999; } // stands in for a slow HTTP call
}

class WarehousePriceService implements PriceService {
    int remoteCalls = 0;
    public int priceOf(String sku) { remoteCalls++; return 1999; } // a different slow source
}

// Caching is welded to HttpPriceService by "extends".
class CachingHttpPriceService extends HttpPriceService {
    private final Map<String, Integer> cache = new HashMap<>();
    @Override
    public int priceOf(String sku) { return cache.computeIfAbsent(sku, super::priceOf); }
}

public class SubclassPerFeatureBroken {
    public static void main(String[] args) {
        CachingHttpPriceService http = new CachingHttpPriceService();
        for (int i = 0; i < 3; i++) http.priceOf("SKU-1");
        System.out.println("HttpPriceService + caching subclass: " + http.remoteCalls + " remote call(s) for 3 lookups");

        // The team switches source. The caching subclass doesn't apply to the new class.
        WarehousePriceService warehouse = new WarehousePriceService();
        for (int i = 0; i < 3; i++) warehouse.priceOf("SKU-1");
        System.out.println("Switched to WarehousePriceService:   " + warehouse.remoteCalls + " remote call(s) for 3 lookups");

        System.out.println("Caching was tied to one class. With 2 sources and 2 features (caching, logging),");
        System.out.println("covering every combination by subclassing takes 6 subclasses: CachingHttp, LoggingHttp,");
        System.out.println("LoggingCachingHttp, and the same three again for Warehouse.");
    }
}
