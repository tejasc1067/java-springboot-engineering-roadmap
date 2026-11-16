package com.example.promql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

/**
 * One Spring Boot app, one {@link FakeTarget}, and one real Prometheus scraping both every second.
 * Started once per test run and shared by every test class.
 */
final class PrometheusEnvironment {

    static final ConfigurableApplicationContext APP;
    static final RestClient APP_HTTP;
    static final FakeTarget FAKE;
    static final GenericContainer<?> PROMETHEUS;
    private static final RestClient PROM_HTTP;
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        try {
            APP = SpringApplication.run(PromqlDemoApplication.class, "--server.port=0");
            int appPort = ((WebServerApplicationContext) APP).getWebServer().getPort();
            APP_HTTP = RestClient.create("http://localhost:" + appPort);
            FAKE = new FakeTarget();

            // Lets the container reach these host ports as host.testcontainers.internal:<port>
            Testcontainers.exposeHostPorts(appPort, FAKE.port());

            String config = """
                    global:
                      scrape_interval: 1s
                    scrape_configs:
                      - job_name: orders-app
                        metrics_path: /actuator/prometheus
                        static_configs:
                          - targets: ['host.testcontainers.internal:%d']
                      - job_name: fake
                        static_configs:
                          - targets: ['host.testcontainers.internal:%d']
                    """.formatted(appPort, FAKE.port());

            PROMETHEUS = new GenericContainer<>("prom/prometheus:v3.5.0")
                    .withCopyToContainer(Transferable.of(config), "/etc/prometheus/prometheus.yml")
                    .withExposedPorts(9090)
                    .waitingFor(Wait.forHttp("/-/ready").forStatusCode(200));
            PROMETHEUS.start();
            PROM_HTTP = RestClient.create("http://" + PROMETHEUS.getHost() + ":" + PROMETHEUS.getMappedPort(9090));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private PrometheusEnvironment() { }

    /** Runs a PromQL query through Prometheus's HTTP API (/api/v1/query) and returns the whole JSON response. */
    static JsonNode query(String promql) {
        try {
            return JSON.readTree(PROM_HTTP.get()
                    .uri(b -> b.path("/api/v1/query").queryParam("query", "{q}").build(promql))
                    .retrieve().body(String.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The numbers of an instant-vector result, one per series. */
    static List<Double> values(String promql) {
        List<Double> values = new ArrayList<>();
        query(promql).path("data").path("result").forEach(r -> values.add(r.path("value").get(1).asDouble()));
        return values;
    }

    static double single(String promql) {
        List<Double> values = values(promql);
        if (values.size() != 1) throw new AssertionError(promql + " returned " + values.size() + " series");
        return values.getFirst();
    }

    /** Waits until Prometheus has scraped a value for which the condition holds (scrapes happen every second). */
    static void awaitScraped(String promql, Predicate<Double> condition) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    List<Double> values = values(promql);
                    return values.size() == 1 && condition.test(values.getFirst());
                });
    }
}
