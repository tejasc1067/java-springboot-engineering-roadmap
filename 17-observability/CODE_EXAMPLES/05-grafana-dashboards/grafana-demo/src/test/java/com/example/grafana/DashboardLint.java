package com.example.grafana;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** A handful of checks a reviewer would make on a service dashboard, run on the dashboard's JSON. */
final class DashboardLint {

    static final int MAX_PANELS = 12;

    // Series that only ever go up: counters (_total), and a histogram's _count, _sum and _bucket.
    private static final Pattern CUMULATIVE = Pattern.compile("\\b[a-zA-Z_:]+_(total|count|sum|bucket)\\b");
    private static final Pattern RATE = Pattern.compile("\\b(rate|irate|increase)\\(");
    private static final Pattern ERROR_FILTER = Pattern.compile("status=~?\"5");
    private static final Pattern BY_ENDPOINT = Pattern.compile("by \\((le, )?uri\\)");

    private DashboardLint() { }

    static List<String> check(JsonNode dashboard) {
        List<String> problems = new ArrayList<>();
        JsonNode panels = dashboard.path("panels");
        boolean hasPercentile = false;
        boolean hasEndpointBreakdown = false;

        for (JsonNode panel : panels) {
            String title = panel.path("title").asText();
            for (JsonNode target : panel.path("targets")) {
                String expr = target.path("expr").asText();
                if (CUMULATIVE.matcher(expr).find() && !RATE.matcher(expr).find()) {
                    problems.add(title + ": COUNTER_WITHOUT_RATE");
                }
                if (ERROR_FILTER.matcher(expr).find() && !expr.contains(" / ")) {
                    problems.add(title + ": ERRORS_NOT_A_RATIO");
                }
                if (expr.contains("_seconds_sum") && expr.contains("_seconds_count") && expr.contains(" / ")) {
                    problems.add(title + ": AVERAGE_LATENCY");
                }
                if (expr.contains("http_server_requests") && !expr.contains("uri!~\"/actuator")) {
                    problems.add(title + ": INCLUDES_ACTUATOR_TRAFFIC");
                }
                hasPercentile |= expr.matches("(?s).*histogram_quantile\\(0\\.9\\d*,.*");
                hasEndpointBreakdown |= expr.contains("http_server_requests") && BY_ENDPOINT.matcher(expr).find();
            }
        }
        if (!hasPercentile) problems.add("dashboard: NO_PERCENTILE_LATENCY");
        if (!hasEndpointBreakdown) problems.add("dashboard: NO_ENDPOINT_BREAKDOWN");
        if (panels.size() > MAX_PANELS) problems.add("dashboard: TOO_MANY_PANELS (" + panels.size() + ")");
        return problems;
    }
}
