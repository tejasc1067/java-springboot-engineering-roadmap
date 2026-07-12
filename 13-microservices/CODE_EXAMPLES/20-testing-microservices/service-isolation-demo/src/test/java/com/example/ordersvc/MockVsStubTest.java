package com.example.ordersvc;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two ways to isolate order-service from inventory-service, and why they are NOT equivalent.
 *
 *   Mock the OBJECT (Mockito): replace InventoryClient with a fake that returns whatever you
 *   tell it. Fast, and fine for exercising the DECISION logic. But it deletes the real client,
 *   so nothing about the HTTP layer (URL, JSON mapping, which exception a 5xx actually throws)
 *   is tested — you assert against your GUESS of how the downstream behaves.
 *
 *   Stub the WIRE (WireMock): keep the real InventoryClient, fake only the network. The real
 *   URL, JSON parsing, and status handling all run, so the test reflects what actually happens.
 *
 * The 500 case below is the gap made concrete: the mock lets us pretend a failed call throws a
 * bare RuntimeException (it doesn't), and that fiction sails through green; the stub reveals the
 * real HttpServerErrorException, which OrderService actually translates to InventoryUnavailable.
 */
class MockVsStubTest {

    @RegisterExtension
    static WireMockExtension inventory = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void mockingTheObjectValidatesTheDecisionLogicFast() {
        InventoryClient fake = mock(InventoryClient.class);
        when(fake.getStock("SKU-BOOK")).thenReturn(new StockResponse("SKU-BOOK", 5));

        Order order = new OrderService(fake).placeOrder("SKU-BOOK", 2);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        // Genuinely useful — but the URL, the JSON parsing, and the 5xx handling never ran.
    }

    @Test
    void mockingLetsAWrongAssumptionAboutTheDownstreamPass() {
        InventoryClient fake = mock(InventoryClient.class);
        // We GUESS a failed call throws a plain RuntimeException. It does not — the real client
        // throws HttpServerErrorException on a 500 — but the mock can't correct us.
        when(fake.getStock("SKU-BOOK")).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> new OrderService(fake).placeOrder("SKU-BOOK", 2))
                .isInstanceOf(RuntimeException.class)
                // Note what did NOT happen: OrderService's catch (RestClientException) never
                // fired, because a bare RuntimeException isn't one. The mock hid that gap.
                .isNotInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void stubbingTheWireRevealsTheRealErrorHandling() {
        inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK")).willReturn(serverError()));
        OrderService real = new OrderService(
                InventoryClients.pointingAt(inventory.baseUrl(), Duration.ofSeconds(2)));

        // Against a real 500 the real client throws HttpServerErrorException (a RestClientException),
        // so OrderService's catch DOES fire and translates it. Only the stub exercised this path.
        assertThatThrownBy(() -> real.placeOrder("SKU-BOOK", 2))
                .isInstanceOf(InventoryUnavailableException.class);
    }
}
