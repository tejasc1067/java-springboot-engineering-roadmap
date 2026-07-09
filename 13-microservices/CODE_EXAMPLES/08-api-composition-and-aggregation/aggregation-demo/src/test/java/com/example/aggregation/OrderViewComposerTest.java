package com.example.aggregation;

import com.example.aggregation.Dtos.OrderInfo;
import com.example.aggregation.Dtos.OrderView;
import com.example.aggregation.Dtos.PaymentInfo;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderViewComposerTest {

    private final Function<Long, OrderInfo> order = id -> new OrderInfo(id, "SKU-BOOK", 2);
    private final Function<Long, PaymentInfo> payment = id -> new PaymentInfo(id, "CAPTURED", 1999);

    @Test
    void composesOneViewFromTwoServices() {
        OrderView view = new OrderViewComposer(order, payment).composeSequential(1L);

        assertThat(view.orderId()).isEqualTo(1L);
        assertThat(view.sku()).isEqualTo("SKU-BOOK");
        assertThat(view.paymentAvailable()).isTrue();
        assertThat(view.payment().status()).isEqualTo("CAPTURED");
    }

    @Test
    void degradesToPartialViewWhenAnOptionalSourceIsDown() {
        Function<Long, PaymentInfo> paymentDown = id -> {
            throw new RuntimeException("payment-service unavailable");
        };

        OrderView view = new OrderViewComposer(order, paymentDown).composeSequential(1L);

        // The view still comes back with order data; the payment section is just absent.
        assertThat(view.orderId()).isEqualTo(1L);
        assertThat(view.paymentAvailable()).isFalse();
        assertThat(view.payment()).isNull();
    }

    @Test
    void failsWhenaRequiredSourceIsDown() {
        Function<Long, OrderInfo> orderDown = id -> {
            throw new RuntimeException("order-service unavailable");
        };

        // Order data is required — there's no order view without it, so this must NOT
        // silently succeed. Not everything can degrade; you choose what's essential.
        assertThatThrownBy(() -> new OrderViewComposer(orderDown, payment).composeSequential(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order-service");
    }

    @Test
    void parallelFanOutIsFasterThanSequential() {
        Function<Long, OrderInfo> slowOrder = id -> sleep(200, new OrderInfo(id, "SKU-BOOK", 2));
        Function<Long, PaymentInfo> slowPayment = id -> sleep(200, new PaymentInfo(id, "CAPTURED", 1999));
        OrderViewComposer composer = new OrderViewComposer(slowOrder, slowPayment);

        long seqStart = System.nanoTime();
        composer.composeSequential(1L);
        long sequentialMs = (System.nanoTime() - seqStart) / 1_000_000;

        long parStart = System.nanoTime();
        composer.composeParallel(1L);
        long parallelMs = (System.nanoTime() - parStart) / 1_000_000;

        // Sequential pays 200 + 200 ≈ 400ms; parallel pays ≈ max(200, 200) ≈ 200ms.
        assertThat(sequentialMs).isGreaterThanOrEqualTo(380L);
        assertThat(parallelMs).isLessThan(380L);
    }

    private static <T> T sleep(long millis, T value) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }
}
