package com.example;

import java.util.Set;

public class InventoryService {

    private final Set<String> inStock = Set.of("BOOK-A", "PEN-B", "MUG-C");

    public boolean inStock(String itemCode) {
        return inStock.contains(itemCode);
    }
}
