package com.example.ordersvc;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * The service under test. It owns no stock data (topic 04) — it must ASK inventory-service
 * before it can accept an order. Every branch below depends on what the downstream returns,
 * which is exactly why it can't be unit-tested with the downstream absent: the test has to
 * supply those responses. Topic 20's answer is to stub the downstream (WireMock) so the real
 * HTTP call runs against canned success AND failure responses.
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
            // inventory-service has never heard of this sku: a definite business answer.
            return new Order(sku, quantity, OrderStatus.REJECTED_UNKNOWN_SKU);
        } catch (RestClientException e) {
            // A 5xx, a read timeout, or a refused connection — NOT a business answer. We can't
            // know the stock level, so we refuse to silently confirm or reject the order.
            throw new InventoryUnavailableException(
                    "inventory-service call failed for sku=" + sku, e);
        }

        if (stock.available() >= quantity) {
            return new Order(sku, quantity, OrderStatus.CONFIRMED);
        }
        return new Order(sku, quantity, OrderStatus.REJECTED_INSUFFICIENT_STOCK);
    }
}
