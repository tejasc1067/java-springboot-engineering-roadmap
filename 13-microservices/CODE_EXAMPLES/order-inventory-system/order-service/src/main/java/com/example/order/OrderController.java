package com.example.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orders;
    private final InventoryClient inventory;

    public OrderController(OrderRepository orders, InventoryClient inventory) {
        this.orders = orders;
        this.inventory = inventory;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequestBody PlaceOrderRequest request) {
        // The cross-service call: order-service asks inventory-service whether it can
        // fulfil the request. It does NOT read inventory's database — it asks over HTTP.
        StockResponse stock;
        try {
            stock = inventory.getStock(request.sku());
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.badRequest().build(); // inventory doesn't know this SKU
        }

        if (stock.available() >= request.quantity()) {
            OrderRecord saved = orders.save(
                    new OrderRecord(request.sku(), request.quantity(), OrderRecord.OrderStatus.CONFIRMED));
            return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(saved));
        }

        OrderRecord rejected = orders.save(
                new OrderRecord(request.sku(), request.quantity(), OrderRecord.OrderStatus.REJECTED));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(OrderResponse.from(rejected));
    }
}
