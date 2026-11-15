package com.example.synccall;

import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The same stub inventory, called through @FeignClient instead of @HttpExchange.
 * Happy path is identical; the failure paths are not - Feign has its own exception
 * types and its own timeout settings, so switching clients is not a drop-in change.
 */
@SpringBootTest(properties = {
        // Feign's default read timeout is 60 seconds. Set it per client, by client name.
        "spring.cloud.openfeign.client.config.inventory.read-timeout=500"
})
class FeignClientTest {

    private static final HttpServer stubInventory = startStubInventory();

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("inventory.base-url", () -> "http://localhost:" + stubInventory.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        stubInventory.stop(0);
    }

    @Autowired
    FeignInventoryClient inventory;

    @Test
    void feignMakesTheSameCallAsTheHttpInterface() {
        StockResponse stock = inventory.getStock("SKU-BOOK");

        assertThat(stock.available()).isEqualTo(5);
    }

    @Test
    void a404IsAFeignExceptionNotAnHttpClientErrorException() {
        // A catch (HttpClientErrorException.NotFound e) copied from RestClient code
        // would never fire here - the 404 would escape as a 500 to your caller.
        assertThatThrownBy(() -> inventory.getStock("NOPE"))
                .isInstanceOf(FeignException.NotFound.class)
                .isNotInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void readTimeoutComesFromFeignConfigNotFromRestClient() {
        assertThatThrownBy(() -> inventory.getStock("SKU-SLOW"))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("timed out");
    }

    private static HttpServer startStubInventory() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.createContext("/api/inventory/", exchange -> {
                String sku = exchange.getRequestURI().getPath().substring("/api/inventory/".length());
                switch (sku) {
                    case "SKU-BOOK" -> respond(exchange, 200, "{\"sku\":\"SKU-BOOK\",\"available\":5}");
                    case "SKU-SLOW" -> {
                        sleep(2_000);
                        respond(exchange, 200, "{\"sku\":\"SKU-SLOW\",\"available\":1}");
                    }
                    default -> respond(exchange, 404, "{\"error\":\"unknown sku\"}");
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        } catch (IOException ignored) {
            // the client already gave up on SKU-SLOW
        }
    }
}
