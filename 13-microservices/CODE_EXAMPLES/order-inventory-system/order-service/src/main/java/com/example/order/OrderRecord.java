package com.example.order;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * order-service's own persistent record of an order, in ITS own database. It stores
 * the SKU and quantity it was asked for and the outcome. It deliberately does NOT
 * store the stock level — that is inventory's data, owned by inventory-service.
 * Class is named OrderRecord because "Order" collides with the SQL reserved word;
 * the table is explicitly named "orders" for the same reason.
 */
@Entity
@Table(name = "orders")
public class OrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected OrderRecord() { // required by JPA
    }

    public OrderRecord(String sku, int quantity, OrderStatus status) {
        this.sku = sku;
        this.quantity = quantity;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public enum OrderStatus {
        CONFIRMED, REJECTED
    }
}
