package com.example.session;

import java.util.ArrayList;
import java.util.List;

/** BROKEN as a session attribute once sessions leave the JVM: not Serializable. */
public class Cart {
    private final List<String> items = new ArrayList<>();

    public void add(String item) {
        items.add(item);
    }

    public List<String> items() {
        return items;
    }
}
