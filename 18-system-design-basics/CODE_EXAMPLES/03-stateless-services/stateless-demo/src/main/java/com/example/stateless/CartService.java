package com.example.stateless;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One instance of the shop's cart service. Where the carts live is decided by whoever creates it:
 * a {@code new HashMap<>()} per instance is "in memory" (like an {@code HttpSession} without Spring Session),
 * one map handed to every instance is a stand-in for a shared store such as Redis (module 15 topic 12).
 */
public class CartService {

    private final String name;
    private final Map<String, List<String>> carts;

    public CartService(String name, Map<String, List<String>> carts) {
        this.name = name;
        this.carts = carts;
    }

    public void add(String sessionId, String item) {
        carts.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(item);
    }

    public List<String> cart(String sessionId) {
        return carts.getOrDefault(sessionId, List.of());
    }

    public String name() {
        return name;
    }
}
