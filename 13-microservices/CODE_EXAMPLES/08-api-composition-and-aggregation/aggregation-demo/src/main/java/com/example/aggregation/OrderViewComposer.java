package com.example.aggregation;

import com.example.aggregation.Dtos.OrderInfo;
import com.example.aggregation.Dtos.OrderView;
import com.example.aggregation.Dtos.PaymentInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Composes an OrderView by calling order-service and payment-service and joining the
 * results. Each downstream is an injected function so tests can make it fast, slow,
 * or failing. Two composition strategies are shown:
 *
 *   composeSequential — call one, then the other. Latency = sum of both.
 *   composeParallel   — call both at once. Latency ≈ the slower of the two.
 *
 * Both degrade gracefully: order data is REQUIRED (no order, no view), but payment is
 * OPTIONAL — if payment-service fails, the view returns with a null payment section
 * instead of failing the whole request.
 */
public class OrderViewComposer {

    private final Function<Long, OrderInfo> orderService;
    private final Function<Long, PaymentInfo> paymentService;

    public OrderViewComposer(Function<Long, OrderInfo> orderService,
                             Function<Long, PaymentInfo> paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    public OrderView composeSequential(Long orderId) {
        OrderInfo order = orderService.apply(orderId);   // required; propagates if it fails
        PaymentInfo payment = tryGetPayment(orderId);    // optional; null on failure
        return join(order, payment);
    }

    public OrderView composeParallel(Long orderId) {
        // Both calls are independent (both keyed by orderId), so fan them out at once.
        CompletableFuture<OrderInfo> orderF =
                CompletableFuture.supplyAsync(() -> orderService.apply(orderId));
        CompletableFuture<PaymentInfo> paymentF =
                CompletableFuture.supplyAsync(() -> paymentService.apply(orderId))
                        .exceptionally(ex -> null); // payment is optional -> degrade to null

        OrderInfo order = orderF.join();                 // required; propagates if it fails
        PaymentInfo payment = paymentF.join();
        return join(order, payment);
    }

    private PaymentInfo tryGetPayment(Long orderId) {
        try {
            return paymentService.apply(orderId);
        } catch (RuntimeException e) {
            return null; // graceful degradation: order view without payment section
        }
    }

    private OrderView join(OrderInfo order, PaymentInfo payment) {
        return new OrderView(order.orderId(), order.sku(), order.quantity(), payment);
    }
}
