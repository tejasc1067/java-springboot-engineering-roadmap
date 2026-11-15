package com.example;

import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.util.ArrayList;
import java.util.List;

// The aggregate root. Saving an Order saves its items; loading an Order loads its items. Always, together.
// ORDER is a reserved word in SQL, so the table has another name. Names given here are QUOTED in the SQL,
// which makes them case-sensitive: H2 stores unquoted names in upper case, so "purchase_order" would not be found.
@Table("PURCHASE_ORDER")
public class Order {

    @Id private Long id;

    // Another aggregate is referenced by ID only - never as a Customer object you can navigate into.
    private AggregateReference<Customer, Long> customerId;

    private String status = "NEW";

    @MappedCollection(idColumn = "ORDER_ID", keyColumn = "LINE_NO")
    private List<OrderItem> items = new ArrayList<>();

    public Order(AggregateReference<Customer, Long> customerId) { this.customerId = customerId; }

    public Order addItem(String sku, int quantity) {
        items.add(new OrderItem(sku, quantity));
        return this;
    }

    public Long getId() { return id; }
    public AggregateReference<Customer, Long> getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<OrderItem> getItems() { return items; }
}
