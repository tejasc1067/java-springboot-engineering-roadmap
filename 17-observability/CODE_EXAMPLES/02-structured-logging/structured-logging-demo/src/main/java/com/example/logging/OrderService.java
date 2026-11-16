package com.example.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public void placeOrder(String orderId, long totalCents) {
        // Key-value pairs become separate JSON fields, so they can be queried, not just grepped.
        log.atInfo()
                .setMessage("order placed")
                .addKeyValue("orderId", orderId)
                .addKeyValue("totalCents", totalCents)
                .log();
    }

    public void failPayment(String orderId) {
        try {
            throw new IllegalStateException("card declined");
        } catch (IllegalStateException e) {
            log.error("payment failed for order {}", orderId, e);
        }
    }
}
