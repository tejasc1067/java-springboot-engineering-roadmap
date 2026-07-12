package com.example.ordersvc;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * order-service's decision logic. It relies on the inventory contract in three ways the consumer
 * test checks against the contract stub: a 200 {sku, available} body it can parse, a 404 that
 * means "unknown SKU", and any other failure meaning "inventory unavailable".
 */
public class OrderService {

    private final InventoryClient inventory;

    public OrderService(InventoryClient inventory) {
        this.inventory = inventory;
    }

    public Order placeOrder(String sku, int quantity) {
        StockResponse stock;
        try {
            stock = inventory.getStock(sku);
        } catch (HttpClientErrorException.NotFound e) {
            return new Order(sku, quantity, OrderStatus.REJECTED_UNKNOWN_SKU);
        } catch (RestClientException e) {
            throw new InventoryUnavailableException(
                    "inventory-service call failed for sku=" + sku, e);
        }

        if (stock.available() >= quantity) {
            return new Order(sku, quantity, OrderStatus.CONFIRMED);
        }
        return new Order(sku, quantity, OrderStatus.REJECTED_INSUFFICIENT_STOCK);
    }
}
