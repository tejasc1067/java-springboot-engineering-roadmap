package com.example.grafana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** No Docker: checks the dashboard files that Grafana provisions in demo.sh. */
class DashboardLintTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theRedAndUseDashboard_passes() throws IOException {
        assertThat(DashboardLint.check(load("service-red.json"))).isEmpty();
    }

    @Test
    void theBrokenDashboard_hidesEveryProblemItIsSupposedToShow() throws IOException {
        assertThat(DashboardLint.check(load("service-broken.json"))).containsExactlyInAnyOrder(
                "Requests: COUNTER_WITHOUT_RATE",          // a total since start: a line that only climbs
                "Requests: INCLUDES_ACTUATOR_TRAFFIC",
                "Errors: COUNTER_WITHOUT_RATE",
                "Errors: ERRORS_NOT_A_RATIO",              // is 10 errors a lot? depends on 100 or 100,000 requests
                "Errors: INCLUDES_ACTUATOR_TRAFFIC",
                "Latency: AVERAGE_LATENCY",                // 5% of requests at 1.5 s barely move an average
                "Latency: INCLUDES_ACTUATOR_TRAFFIC",      // fast scrape requests drag the average down further
                "Request rate: INCLUDES_ACTUATOR_TRAFFIC",
                "dashboard: NO_PERCENTILE_LATENCY",
                "dashboard: NO_ENDPOINT_BREAKDOWN");       // one slow endpoint disappears into the total
    }

    @Test
    void aDashboardWithEverything_isADashboardNobodyReads() throws IOException {
        ObjectNode crowded = (ObjectNode) load("service-red.json");
        ArrayNode panels = (ArrayNode) crowded.path("panels");
        JsonNode cpu = panels.get(panels.size() - 1);
        while (panels.size() < 20) panels.add(cpu.deepCopy());

        assertThat(DashboardLint.check(crowded)).containsExactly("dashboard: TOO_MANY_PANELS (20)");
    }

    private static JsonNode load(String file) throws IOException {
        return JSON.readTree(new File("dashboards", file));
    }
}
