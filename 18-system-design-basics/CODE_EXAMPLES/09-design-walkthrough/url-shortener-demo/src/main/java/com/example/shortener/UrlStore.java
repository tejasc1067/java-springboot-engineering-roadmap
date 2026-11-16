package com.example.shortener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Stands in for the database table: code is the primary key, so a second insert of the same code is refused. */
public class UrlStore {

    private final Map<String, String> urlByCode = new ConcurrentHashMap<>();
    private final AtomicInteger reads = new AtomicInteger();

    /** Like INSERT with a unique key: false instead of overwriting someone else's link. */
    public boolean insert(String code, String longUrl) {
        return urlByCode.putIfAbsent(code, longUrl) == null;
    }

    public Optional<String> find(String code) {
        reads.incrementAndGet();
        return Optional.ofNullable(urlByCode.get(code));
    }

    public int reads() {
        return reads.get();
    }

    public int size() {
        return urlByCode.size();
    }
}
