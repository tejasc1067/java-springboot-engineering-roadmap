package com.example;

// Part of the Order aggregate. No repository and no @Id of its own: it is only ever saved and loaded
// with its order, and identified by (order_id, line_no).
public class OrderItem {
    private String sku;
    private int quantity;

    public OrderItem(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
}
