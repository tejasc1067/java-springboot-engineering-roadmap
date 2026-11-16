package com.example.session;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Two copies of the same app on two random ports, as if behind a load balancer, plus one "browser":
 * an HttpClient whose cookie store sends the same cookies to both (cookies ignore the port).
 */
class TwoInstances implements AutoCloseable {

    final ConfigurableApplicationContext a;
    final ConfigurableApplicationContext b;
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).build();

    TwoInstances(String... sharedProperties) {
        a = start("A", sharedProperties);
        b = start("B", sharedProperties);
    }

    private static ConfigurableApplicationContext start(String name, String... sharedProperties) {
        // Passed as command-line arguments, which override application.yml.
        List<String> args = new ArrayList<>(List.of("--server.port=0", "--app.instance-name=" + name));
        for (String property : sharedProperties) {
            args.add("--" + property);
        }
        return new SpringApplicationBuilder(SessionApp.class).run(args.toArray(String[]::new));
    }

    HttpResponse<String> post(ConfigurableApplicationContext instance, String pathAndQuery) {
        return send(HttpRequest.newBuilder(uri(instance, pathAndQuery)).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    HttpResponse<String> get(ConfigurableApplicationContext instance, String path) {
        return send(HttpRequest.newBuilder(uri(instance, path)).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return browser.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static URI uri(ConfigurableApplicationContext instance, String pathAndQuery) {
        return URI.create("http://localhost:" + instance.getEnvironment().getProperty("local.server.port") + pathAndQuery);
    }

    @Override
    public void close() {
        a.close();
        b.close();
    }
}
