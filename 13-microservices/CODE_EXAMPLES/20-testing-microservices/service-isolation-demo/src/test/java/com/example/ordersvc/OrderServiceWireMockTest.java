package com.example.ordersvc;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test order-service in ISOLATION: inventory-service is never started. WireMock is a real HTTP
 * server we program with canned responses, so the REAL InventoryClient (its URL, its JSON
 * parsing, its status handling) runs against it. That is the difference from mocking the client
 * object (see MockVsStubTest): here the HTTP layer is actually exercised, and we can reproduce
 * the downstream's success AND failure responses deterministically — including ones that are
 * hard to trigger against a real service (a 500, a slow response).
 */
class OrderServiceWireMockTest {

    // Starts a real HTTP server on a random free port before the tests, stops it after,
    // and resets the stubs between each test.
    @RegisterExtension
    static WireMockExtension inventory = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OrderService orderService() {
        return new OrderService(
                InventoryClients.pointingAt(inventory.baseUrl(), Duration.ofSeconds(2)));
    }

    @Test
    void confirmsAnOrderWhenInventoryReportsEnoughStock() {
        inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK"))
                .willReturn(okJson("{\"sku\":\"SKU-BOOK\",\"available\":5}")));

        Order order = orderService().placeOrder("SKU-BOOK", 2);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        // The stub also lets us assert what order-service actually put on the wire — that it
        // called the right endpoint exactly once (a mock of the client object could not).
        inventory.verify(exactly(1), getRequestedFor(urlEqualTo("/api/inventory/SKU-BOOK")));
    }

    @Test
    void rejectsAnOrderWhenInventoryReportsTooFewUnits() {
        inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK"))
                .willReturn(okJson("{\"sku\":\"SKU-BOOK\",\"available\":1}")));

        Order order = orderService().placeOrder("SKU-BOOK", 2);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED_INSUFFICIENT_STOCK);
    }

    @Test
    void rejectsAsUnknownSkuWhenInventoryReturns404() {
        inventory.stubFor(get(urlEqualTo("/api/inventory/NOPE")).willReturn(notFound()));

        Order order = orderService().placeOrder("NOPE", 1);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED_UNKNOWN_SKU);
    }

    @Test
    void surfacesAControlledErrorWhenInventoryReturns500() {
        inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK")).willReturn(serverError()));

        // A downstream 5xx is not a business answer: order-service must not silently confirm or
        // reject. It raises a controlled InventoryUnavailableException (-> 503 to its own caller)
        // and never leaks the raw HttpServerErrorException.
        assertThatThrownBy(() -> orderService().placeOrder("SKU-BOOK", 2))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void treatsASlowDownstreamAsUnavailableInsteadOfHanging() {
        inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK"))
                .willReturn(okJson("{\"sku\":\"SKU-BOOK\",\"available\":5}").withFixedDelay(1500)));

        // A stub can inject latency on demand, so the timeout path (topic 09) is testable
        // deterministically — no need for the real inventory-service to actually be slow.
        OrderService withTightTimeout = new OrderService(
                InventoryClients.pointingAt(inventory.baseUrl(), Duration.ofMillis(300)));

        assertThatThrownBy(() -> withTightTimeout.placeOrder("SKU-BOOK", 2))
                .isInstanceOf(InventoryUnavailableException.class);
    }
}
