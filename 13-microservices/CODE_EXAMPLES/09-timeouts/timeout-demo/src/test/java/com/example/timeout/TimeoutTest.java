package com.example.timeout;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The timeout contrast. A stub "inventory-service" is deliberately slow (1500ms).
 * A caller with a generous read timeout WAITS the whole time; a caller with a tight
 * read timeout FAILS FAST. In production the first behaviour is what ties up threads
 * until the whole service falls over — a slow downstream is more dangerous than a
 * dead one, precisely because without a timeout you wait for it.
 */
class TimeoutTest {

    private static final int SLOW_MS = 1500;
    private static HttpServer slowInventory;
    private static String baseUrl;

    @BeforeAll
    static void startSlowInventory() throws IOException {
        slowInventory = HttpServer.create(new InetSocketAddress(0), 0);
        slowInventory.createContext("/api/inventory/", exchange -> {
            sleep(SLOW_MS); // the downstream is slow, not dead — it WILL respond, eventually
            byte[] body = "{\"sku\":\"SKU-BOOK\",\"available\":5}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        slowInventory.start();
        baseUrl = "http://localhost:" + slowInventory.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        slowInventory.stop(0);
    }

    private RestClient clientWithReadTimeout(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    @Test
    void withoutATightTimeoutTheCallerWaitsForTheSlowDownstream() {
        RestClient client = clientWithReadTimeout(Duration.ofSeconds(5)); // effectively unbounded here

        long start = System.nanoTime();
        String body = client.get().uri("/api/inventory/SKU-BOOK").retrieve().body(String.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(body).contains("SKU-BOOK");
        // It succeeded — but only after blocking for the full slow duration. That thread
        // was unavailable to serve anyone else the whole time.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(SLOW_MS - 150L);
    }

    @Test
    void aTightTimeoutFailsFastInsteadOfHanging() {
        RestClient client = clientWithReadTimeout(Duration.ofMillis(300));

        long start = System.nanoTime();
        assertThatThrownBy(() ->
                client.get().uri("/api/inventory/SKU-BOOK").retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class); // wraps java.net.SocketTimeoutException
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Gave up around 300ms instead of waiting the full 1500ms. The thread is freed
        // to do useful work (retry, fall back, return an error) rather than hang.
        assertThat(elapsedMs).isLessThan(SLOW_MS - 300L);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
