package com.example.promql;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A scrape target whose numbers the test sets by hand, so it can do what a real app can't do on command:
 * restart (a counter drops to a low value) or report exact histogram buckets.
 */
class FakeTarget {

    private final Map<String, String> series = new ConcurrentHashMap<>();
    private final HttpServer server;

    FakeTarget() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/metrics", exchange -> {
            StringBuilder body = new StringBuilder();
            new TreeMap<>(series).forEach((name, value) -> body.append(name).append(' ').append(value).append('\n'));
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            // Prometheus 3 refuses a scrape without a valid Content-Type.
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    void set(String seriesWithLabels, double value) {
        series.put(seriesWithLabels, String.valueOf(value));
    }
}
