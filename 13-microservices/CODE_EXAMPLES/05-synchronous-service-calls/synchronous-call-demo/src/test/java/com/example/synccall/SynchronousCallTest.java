package com.example.synccall;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the synchronous call against a stub "inventory-service" running in-process
 * (JDK HttpServer), so the mechanics are visible with nothing else to start:
 *   - the declarative @HttpExchange interface issues the GET and parses the JSON,
 *   - the plain RestClient does the same call inline,
 *   - a 404 from the downstream surfaces as HttpClientErrorException.NotFound
 *     (one of the four outcomes every synchronous call must plan for).
 */
class SynchronousCallTest {

    private static HttpServer stubInventory;
    private static String baseUrl;

    @BeforeAll
    static void startStubInventory() throws IOException {
        stubInventory = HttpServer.create(new InetSocketAddress(0), 0); // port 0 = pick a free port
        stubInventory.createContext("/api/inventory/", exchange -> {
            String sku = exchange.getRequestURI().getPath().substring("/api/inventory/".length());
            if (sku.equals("SKU-BOOK")) {
                respond(exchange, 200, "{\"sku\":\"SKU-BOOK\",\"available\":5}");
            } else {
                respond(exchange, 404, "{\"error\":\"unknown sku\"}"); // inventory doesn't know this SKU
            }
        });
        stubInventory.start();
        baseUrl = "http://localhost:" + stubInventory.getAddress().getPort();
    }

    @AfterAll
    static void stopStubInventory() {
        stubInventory.stop(0);
    }

    @Test
    void declarativeInterfaceIssuesTheCallAndParsesTheBody() {
        InventoryClient inventory = InventoryClients.declarative(baseUrl);

        StockResponse stock = inventory.getStock("SKU-BOOK");

        assertThat(stock.sku()).isEqualTo("SKU-BOOK");
        assertThat(stock.available()).isEqualTo(5);
    }

    @Test
    void plainRestClientMakesTheSameCall() {
        StockResponse stock = InventoryClients.getStockInline(baseUrl, "SKU-BOOK");

        assertThat(stock.available()).isEqualTo(5);
    }

    @Test
    void a404FromTheDownstreamSurfacesAsNotFound() {
        InventoryClient inventory = InventoryClients.declarative(baseUrl);

        assertThatThrownBy(() -> inventory.getStock("NOPE"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
