package com.example;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class WarmedCache {

    private final Map<String, String> entries = new HashMap<>();

    @PostConstruct
    public void warm() {
        entries.put("ABC", "alphabet");
        entries.put("XYZ", "ending");
        System.out.println("WarmedCache: warmed " + entries.size() + " entries");
    }

    @PreDestroy
    public void clear() {
        int count = entries.size();
        entries.clear();
        System.out.println("WarmedCache: cleared " + count + " entries");
    }

    public String get(String key) {
        return entries.get(key);
    }

    public int size() {
        return entries.size();
    }
}
