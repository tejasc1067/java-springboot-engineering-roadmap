package com.example.shortener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ShortenerService {

    static final int MAX_ATTEMPTS = 5;

    public record Redirect(int status, String location) { }

    private final CodeGenerator generator;
    private final UrlStore store;
    private final int redirectStatus;   // 301 or 302, see the topic's "API" section

    // ponytail: in-memory maps stand in for a request_id unique column and for Redis
    private final Map<String, String> codeByRequestId = new ConcurrentHashMap<>();
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger cacheMisses = new AtomicInteger();

    public ShortenerService(CodeGenerator generator, UrlStore store, int redirectStatus) {
        this.generator = generator;
        this.store = store;
        this.redirectStatus = redirectStatus;
    }

    /** A client that retries after a timeout sends the same requestId and gets the same code back. */
    public String shorten(String requestId, String longUrl) {
        if (requestId == null) return createNew(longUrl);
        return codeByRequestId.computeIfAbsent(requestId, id -> createNew(longUrl));
    }

    private String createNew(String longUrl) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String code = generator.generate(longUrl);
            if (store.insert(code, longUrl)) return code;
            // Taken. Fine if it's already this URL (hash codes repeat for the same URL); otherwise try another code.
            if (store.find(code).filter(longUrl::equals).isPresent()) return code;
        }
        throw new IllegalStateException("no free code after " + MAX_ATTEMPTS + " attempts for " + longUrl);
    }

    public void claimAlias(String alias, String longUrl) {
        if (!store.insert(alias, longUrl)) throw new IllegalArgumentException("alias already taken: " + alias);
    }

    /** Cache-aside: try the cache, on a miss read the store and fill the cache. */
    public Redirect resolve(String code) {
        String cached = cache.get(code);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return new Redirect(redirectStatus, cached);
        }
        cacheMisses.incrementAndGet();
        Optional<String> found = store.find(code);
        if (found.isEmpty()) return new Redirect(404, null);
        cache.put(code, found.get());
        return new Redirect(redirectStatus, found.get());
    }

    public int cacheHits() {
        return cacheHits.get();
    }

    public int cacheMisses() {
        return cacheMisses.get();
    }
}
