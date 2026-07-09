package com.example.tracing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots order-service with a stub inventory-service behind it and shows one correlation
 * id spanning the hop: order-service generates (or reuses) an id, and inventory-service
 * receives that SAME id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationPropagationTest {

    private static HttpServer downstream;
    private static final AtomicReference<String> downstreamReceivedId = new AtomicReference<>();

    @DynamicPropertySource
    static void startDownstream(DynamicPropertyRegistry registry) throws IOException {
        downstream = HttpServer.create(new InetSocketAddress(0), 0);
        downstream.createContext("/api/inventory/", exchange -> {
            downstreamReceivedId.set(exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER));
            byte[] body = "{\"sku\":\"SKU-BOOK\",\"available\":5}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        downstream.start();
        registry.add("inventory.uri", () -> "http://localhost:" + downstream.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        downstream.stop(0);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void generatesAnIdWhenNoneIsSuppliedAndPropagatesItDownstream() {
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + port + "/api/order/SKU-BOOK", String.class);

        String assignedId = response.getHeaders().getFirst(CorrelationIdFilter.HEADER);
        assertThat(assignedId).isNotBlank();                         // order-service assigned one
        assertThat(downstreamReceivedId.get()).isEqualTo(assignedId); // inventory saw the SAME id
    }

    @Test
    void reusesAnUpstreamIdSoTheChainSharesOne() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "trace-abc-123"); // as if an upstream gateway set it

        ResponseEntity<String> response = rest.exchange(
                "http://localhost:" + port + "/api/order/SKU-BOOK",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo("trace-abc-123");
        assertThat(downstreamReceivedId.get()).isEqualTo("trace-abc-123"); // same id all the way through
    }
}
