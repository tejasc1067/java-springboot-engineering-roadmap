package com.example.cachepatterns;

/**
 * Cache-aside: the application talks to both the cache and the database itself.
 *
 * The two pause points mark where a real thread can be descheduled, stall on GC, or wait on the network.
 * In production they're empty; tests put "what another request did meanwhile" there to replay a race.
 */
public class CacheAside {

    private final Database database;
    private final Cache cache;
    private static final Runnable NOTHING = () -> { };

    private Runnable afterDatabaseRead = NOTHING;
    private Runnable betweenWriteSteps = NOTHING;

    public CacheAside(Database database, Cache cache) {
        this.database = database;
        this.cache = cache;
    }

    public Long get(String sku) {
        Long cached = cache.get(sku);
        if (cached != null) return cached;
        Long loaded = database.find(sku);
        pauseAfterDatabaseRead();
        if (loaded != null) cache.put(sku, loaded);
        return loaded;
    }

    /** The recommended order: write the database, then delete the cached copy. */
    public void updateThenDelete(String sku, long cents) {
        database.save(sku, cents);
        pauseBetweenWriteSteps();
        cache.delete(sku);
    }

    /** Broken order: delete first, then write. A read in between puts the old value back. */
    public void deleteThenUpdate(String sku, long cents) {
        cache.delete(sku);
        pauseBetweenWriteSteps();
        database.save(sku, cents);
    }

    /** Broken: write the new value into the cache instead of deleting. Two writers can finish out of order. */
    public void updateThenSetCache(String sku, long cents) {
        database.save(sku, cents);
        pauseBetweenWriteSteps();
        cache.put(sku, cents);
    }

    public void onceAfterDatabaseRead(Runnable otherRequest) {
        afterDatabaseRead = otherRequest;
    }

    public void onceBetweenWriteSteps(Runnable otherRequest) {
        betweenWriteSteps = otherRequest;
    }

    // Each pause fires once, so the "other request" can itself call get() or update() without recursing.
    private void pauseAfterDatabaseRead() {
        Runnable otherRequest = afterDatabaseRead;
        afterDatabaseRead = NOTHING;
        otherRequest.run();
    }

    private void pauseBetweenWriteSteps() {
        Runnable otherRequest = betweenWriteSteps;
        betweenWriteSteps = NOTHING;
        otherRequest.run();
    }
}
