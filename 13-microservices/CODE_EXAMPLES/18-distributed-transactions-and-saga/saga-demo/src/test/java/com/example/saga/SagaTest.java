package com.example.saga;

import com.example.saga.Participants.InventoryService;
import com.example.saga.Participants.OrderService;
import com.example.saga.Participants.PaymentService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaTest {

    private final InventoryService inventory = new InventoryService();
    private final PaymentService payment = new PaymentService();
    private final OrderService order = new OrderService();

    private Saga placeOrderSaga(String sku, int qty, long cents,
                                AtomicReference<String> chargeId, AtomicReference<String> orderId) {
        return new Saga()
                .step("reserve-stock",
                        () -> inventory.reserve(sku, qty),
                        () -> inventory.releaseReservation(sku, qty))
                .step("charge-payment",
                        () -> chargeId.set(payment.charge(cents)),
                        () -> payment.refund(chargeId.get()))
                .step("create-order",
                        () -> orderId.set(order.createOrder(sku, qty)),
                        () -> order.cancelOrder(orderId.get()));
    }

    @Test
    void happyPathCommitsEveryStep() {
        placeOrderSaga("SKU-BOOK", 2, 1999L, new AtomicReference<>(), new AtomicReference<>()).execute();

        assertThat(inventory.available("SKU-BOOK")).isEqualTo(3); // 5 - 2 reserved
        assertThat(payment.charges()).hasSize(1);
        assertThat(order.orders()).hasSize(1);
    }

    @Test
    void whenPaymentFailsTheStockReservationIsCompensated() {
        payment.declineCharges(true); // step 2 will throw

        assertThatThrownBy(() ->
                placeOrderSaga("SKU-BOOK", 2, 1999L, new AtomicReference<>(), new AtomicReference<>()).execute())
                .isInstanceOf(Saga.SagaFailedException.class);

        // reserve-stock committed, then payment failed -> the reservation was released.
        assertThat(inventory.available("SKU-BOOK")).isEqualTo(5); // back to the start; no stock leaked
        assertThat(payment.charges()).isEmpty();                  // the failed charge left nothing
        assertThat(order.orders()).isEmpty();                     // never reached
    }

    @Test
    void compensationsRunInReverseOrderOfCompletion() {
        order.failCreate(true); // step 3 will throw, after steps 1 and 2 committed
        List<String> compensationOrder = new ArrayList<>();
        AtomicReference<String> chargeId = new AtomicReference<>();

        Saga saga = new Saga()
                .step("reserve-stock",
                        () -> inventory.reserve("SKU-BOOK", 2),
                        () -> { compensationOrder.add("release-stock"); inventory.releaseReservation("SKU-BOOK", 2); })
                .step("charge-payment",
                        () -> chargeId.set(payment.charge(1999L)),
                        () -> { compensationOrder.add("refund"); payment.refund(chargeId.get()); })
                .step("create-order",
                        () -> order.createOrder("SKU-BOOK", 2),
                        () -> compensationOrder.add("cancel-order"));

        assertThatThrownBy(saga::execute).isInstanceOf(Saga.SagaFailedException.class);

        // Completed steps were reserve then charge; they compensate in reverse: refund, then release.
        // create-order never completed, so its compensation does not run.
        assertThat(compensationOrder).containsExactly("refund", "release-stock");
        assertThat(inventory.available("SKU-BOOK")).isEqualTo(5); // net effect: consistent again
        assertThat(order.orders()).isEmpty();
    }
}
