package com.example.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Boots the gateway on a random port with a stub "inventory-service" behind it, and
 * proves the gateway ROUTES the request, REWRITES the path, and ADDS a header — the
 * edge concerns that live in the gateway rather than in the downstream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static HttpServer downstream;
    private static final AtomicReference<String> receivedPath = new AtomicReference<>();
    private static final AtomicReference<String> receivedGatewayHeader = new AtomicReference<>();

    @DynamicPropertySource
    static void startDownstreamAndPointGatewayAtIt(DynamicPropertyRegistry registry) throws IOException {
        downstream = HttpServer.create(new InetSocketAddress(0), 0);
        downstream.createContext("/api/inventory/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());                 // what path arrived?
            receivedGatewayHeader.set(exchange.getRequestHeaders().getFirst("X-Gateway")); // header added?
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
    static void stopDownstream() {
        downstream.stop(0);
    }

    @LocalServerPort
    int gatewayPort;

    @Autowired
    TestRestTemplate rest;

    @Test
    void routesRewritesPathAndAddsEdgeHeader() {
        // Client calls the GATEWAY at /inventory/SKU-BOOK — it doesn't know inventory's address.
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + gatewayPort + "/inventory/SKU-BOOK", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("SKU-BOOK");            // routed through to the downstream
        assertThat(receivedPath.get()).isEqualTo("/api/inventory/SKU-BOOK"); // path rewritten at the edge
        assertThat(receivedGatewayHeader.get()).isEqualTo("spring-cloud-gateway-mvc"); // header added at the edge
    }
}
