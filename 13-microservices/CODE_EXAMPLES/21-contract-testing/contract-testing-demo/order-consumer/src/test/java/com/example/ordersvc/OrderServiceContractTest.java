package com.example.ordersvc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.contract.stubrunner.junit.StubRunnerExtension;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CONSUMER half of consumer-driven contract testing. Stub Runner resolves
 * inventory-provider's stubs jar from ~/.m2 (LOCAL mode — no network), boots a WireMock server
 * from the contract-generated mappings, and the REAL order-service client runs against it.
 *
 * The difference from topic 20's WireMock test: there, WE hand-wrote the stub, so it could drift
 * from what inventory-service actually returns. Here the stub is GENERATED from the same contract
 * the provider is verified against — so if the provider stops honouring the contract, the
 * PROVIDER'S build fails (a generated test goes red), not this consumer silently passing against
 * a stale stub. One contract keeps both sides honest.
 *
 * Requires the provider's stubs jar to be installed first: run `mvn install` from the
 * contract-testing-demo root (the reactor builds inventory-provider before this module).
 */
class OrderServiceContractTest {

    @RegisterExtension
    static StubRunnerExtension stubRunner = new StubRunnerExtension()
            .downloadStub("com.example", "inventory-provider", "1.0.0", "stubs")
            .stubsMode(StubRunnerProperties.StubsMode.LOCAL);

    private OrderService orderService() {
        String baseUrl = stubRunner.findStubUrl("com.example", "inventory-provider").toString();
        return new OrderService(InventoryClients.pointingAt(baseUrl, Duration.ofSeconds(2)));
    }

    @Test
    void confirmsWhenTheContractStubReportsEnoughStock() {
        // Runs against the shouldReturnStockForKnownSku contract: 200 {sku:SKU-BOOK, available:5}.
        Order order = orderService().placeOrder("SKU-BOOK", 2);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsWhenTheContractStubReportsTooFewUnits() {
        // Same stub (available:5), but an order for more than that — decided client-side.
        Order order = orderService().placeOrder("SKU-BOOK", 99);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED_INSUFFICIENT_STOCK);
    }

    @Test
    void rejectsUnknownSkuFromTheContract404() {
        // Runs against the shouldReturn404ForUnknownSku contract: a 404 for an unknown SKU.
        Order order = orderService().placeOrder("NO-SUCH-SKU", 1);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED_UNKNOWN_SKU);
    }
}
