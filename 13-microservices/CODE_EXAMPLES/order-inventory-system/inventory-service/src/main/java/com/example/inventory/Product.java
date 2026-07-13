package com.example.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Inventory's model of a product: a SKU and how many are on hand. This is the
 * inventory bounded context (topic 03) — it knows about stock, not about price,
 * descriptions, or images. The order service has its own, different notion of a
 * product; the two are linked only by the SKU string, never by a shared table.
 */
@Entity
public class Product {

    @Id
    private String sku;
    private int availableStock;

    protected Product() { // required by JPA
    }

    public Product(String sku, int availableStock) {
        this.sku = sku;
        this.availableStock = availableStock;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailableStock() {
        return availableStock;
    }
}
