package com.example.aggregation;

/**
 * The pieces a composed "order detail" view is assembled from. OrderInfo comes from
 * order-service and PaymentInfo from payment-service — two services, each keyed by
 * orderId, each owning its own data (topic 04). OrderView is the joined result the
 * composition returns to the caller.
 *
 * paymentInfo is nullable on purpose: if payment-service is unavailable, the view can
 * still return with order data and a null payment section (graceful degradation),
 * rather than failing the whole request.
 */
public final class Dtos {

    public record OrderInfo(Long orderId, String sku, int quantity) {
    }

    public record PaymentInfo(Long orderId, String status, long amountCents) {
    }

    public record OrderView(Long orderId, String sku, int quantity, PaymentInfo payment) {
        public boolean paymentAvailable() {
            return payment != null;
        }
    }

    private Dtos() {
    }
}
