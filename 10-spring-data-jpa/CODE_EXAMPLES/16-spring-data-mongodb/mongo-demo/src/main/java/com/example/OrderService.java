package com.example;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final MongoTemplate mongo;

    public OrderService(OrderRepository orders, MongoTemplate mongo) {
        this.orders = orders;
        this.mongo = mongo;
    }

    // Read-modify-write: load the whole document, change it in Java, save the whole document back.
    // save() REPLACES the stored document, so a concurrent change made in between is overwritten.
    public void addItemBySave(String orderId, OrderItem item) {
        Order order = orders.findById(orderId).orElseThrow();
        order.getItems().add(item);
        orders.save(order);
    }

    // Targeted update: send only "append this item" ($push). MongoDB applies it atomically
    // inside the one document, so concurrent pushes don't overwrite each other.
    public void addItemAtomically(String orderId, OrderItem item) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(orderId)),
                new Update().push("items", item), Order.class);
    }
}
