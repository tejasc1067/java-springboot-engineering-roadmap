package com.example.saga;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Three in-process stand-ins for the services a "place order" saga touches. Each holds
 * its own state (its own "database") and offers a forward operation and its compensation.
 * Any can be told to fail, to drive the compensation path.
 */
public final class Participants {

    private Participants() {
    }

    /** Owns stock. reserve() is the forward step; releaseReservation() the compensation. */
    public static final class InventoryService {
        private final Map<String, Integer> available = new HashMap<>(Map.of("SKU-BOOK", 5));

        public void reserve(String sku, int qty) {
            int have = available.getOrDefault(sku, 0);
            if (have < qty) {
                throw new RuntimeException("insufficient stock for " + sku);
            }
            available.put(sku, have - qty);
        }

        public void releaseReservation(String sku, int qty) {
            available.merge(sku, qty, Integer::sum);
        }

        public int available(String sku) {
            return available.getOrDefault(sku, 0);
        }
    }

    /** Owns payments. charge() is the forward step; refund() the compensation. */
    public static final class PaymentService {
        private boolean declineCharges = false;
        private final List<String> charges = new ArrayList<>();
        private final List<String> refunds = new ArrayList<>();

        public void declineCharges(boolean decline) {
            this.declineCharges = decline;
        }

        public String charge(long cents) {
            if (declineCharges) {
                throw new RuntimeException("payment declined");
            }
            String chargeId = "charge-" + charges.size();
            charges.add(chargeId);
            return chargeId;
        }

        public void refund(String chargeId) {
            refunds.add(chargeId);
        }

        public List<String> charges() {
            return charges;
        }

        public List<String> refunds() {
            return refunds;
        }
    }

    /** Owns orders. createOrder() is the forward step; cancelOrder() the compensation. */
    public static final class OrderService {
        private boolean failCreate = false;
        private final List<String> orders = new ArrayList<>();

        public void failCreate(boolean fail) {
            this.failCreate = fail;
        }

        public String createOrder(String sku, int qty) {
            if (failCreate) {
                throw new RuntimeException("order database unavailable");
            }
            String orderId = "order-" + orders.size();
            orders.add(orderId);
            return orderId;
        }

        public void cancelOrder(String orderId) {
            orders.remove(orderId);
        }

        public List<String> orders() {
            return orders;
        }
    }
}
