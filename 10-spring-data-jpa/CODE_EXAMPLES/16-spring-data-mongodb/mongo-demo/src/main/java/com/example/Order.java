package com.example;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

// One Order = one MongoDB document. Items and the shipping address are stored INSIDE it,
// not in other collections, so there is no join and no lazy loading.
@Document("orders")
public class Order {

    @Id
    private String id;              // MongoDB generates an ObjectId string if this is null on save
    private String customerId;      // a reference: the customer's name and email live in "customers"
    private Address shippingAddress;
    private List<OrderItem> items = new ArrayList<>();
    private String status = "NEW";

    public Order() { }

    public Order(String customerId, Address shippingAddress) {
        this.customerId = customerId;
        this.shippingAddress = shippingAddress;
    }

    public Order addItem(String sku, int quantity, long unitPriceCents) {
        items.add(new OrderItem(sku, quantity, unitPriceCents));
        return this;
    }

    public long totalCents() {
        return items.stream().mapToLong(i -> i.quantity() * i.unitPriceCents()).sum();
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public Address getShippingAddress() { return shippingAddress; }
    public List<OrderItem> getItems() { return items; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
