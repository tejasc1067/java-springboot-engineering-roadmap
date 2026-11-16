package com.example.cdn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A toy CDN edge: a shared cache between users and the origin (our app).
 * It follows the main HTTP caching rules (RFC 9111): Cache-Control max-age, s-maxage, public,
 * private, no-store and no-cache; ETag revalidation; Vary; and the Authorization rule.
 * The clock is simulated in seconds.
 */
public final class EdgeCache {

    public record Request(String path, Map<String, String> headers) {
        public Request(String path) { this(path, Map.of()); }
        String header(String name) { return headers.get(name); }
    }

    public record Response(int status, Map<String, String> headers, String body) {
        public Response {
            Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            caseInsensitive.putAll(headers);
            headers = caseInsensitive;
        }
        public String header(String name) { return headers.get(name); }
    }

    @FunctionalInterface
    public interface Origin { Response fetch(Request request); }

    private record Entry(Response response, Map<String, String> varyValues, long storedAt) { }

    private final Origin origin;
    private final Map<String, List<Entry>> entries = new HashMap<>();   // URL -> one entry per Vary variant
    private long now;
    public int hits, misses, revalidations;

    public EdgeCache(Origin origin) { this.origin = origin; }

    public void advance(long seconds) { now += seconds; }

    public Response get(Request request) {
        List<Entry> variants = entries.computeIfAbsent(request.path(), k -> new ArrayList<>());
        Entry entry = variants.stream().filter(e -> matchesVary(e, request)).findFirst().orElse(null);

        if (entry != null) {
            Map<String, String> cc = cacheControl(entry.response());
            if (!cc.containsKey("no-cache") && now - entry.storedAt() < freshFor(cc)) {
                hits++;
                return entry.response();
            }
            String etag = entry.response().header("ETag");
            if (etag != null) {
                Map<String, String> conditional = new HashMap<>(request.headers());
                conditional.put("If-None-Match", etag);
                Response answer = origin.fetch(new Request(request.path(), conditional));
                if (answer.status() == 304) {   // still valid: keep the body we have, reset its age
                    revalidations++;
                    variants.remove(entry);
                    variants.add(new Entry(entry.response(), entry.varyValues(), now));
                    return entry.response();
                }
                variants.remove(entry);
                return store(request, answer, variants);
            }
            variants.remove(entry);
        }
        return store(request, origin.fetch(request), variants);
    }

    private Response store(Request request, Response response, List<Entry> variants) {
        misses++;
        if (storable(request, response)) {
            Map<String, String> varyValues = new HashMap<>();
            String vary = response.header("Vary");
            if (vary != null) {
                for (String name : vary.split(",")) {
                    String value = request.header(name.trim());
                    varyValues.put(name.trim(), value == null ? "" : value);
                }
            }
            variants.add(new Entry(response, varyValues, now));
        }
        return response;
    }

    private static boolean storable(Request request, Response response) {
        Map<String, String> cc = cacheControl(response);
        if (response.status() != 200 || cc.containsKey("no-store") || cc.containsKey("private")) return false;
        // A shared cache must not reuse a response to a request with credentials in Authorization,
        // unless the response explicitly allows it. Cookies get no such protection.
        if (request.header("Authorization") != null && !cc.containsKey("public") && !cc.containsKey("s-maxage")) {
            return false;
        }
        return cc.containsKey("max-age") || cc.containsKey("s-maxage") || cc.containsKey("no-cache");
    }

    private static boolean matchesVary(Entry entry, Request request) {
        for (Map.Entry<String, String> v : entry.varyValues().entrySet()) {
            String value = request.header(v.getKey());
            if (!v.getValue().equals(value == null ? "" : value)) return false;
        }
        return true;
    }

    /** A shared cache uses s-maxage when present, otherwise max-age. */
    private static long freshFor(Map<String, String> cc) {
        String seconds = cc.getOrDefault("s-maxage", cc.get("max-age"));
        return seconds == null ? 0 : Long.parseLong(seconds);
    }

    private static Map<String, String> cacheControl(Response response) {
        Map<String, String> directives = new HashMap<>();
        String header = response.header("Cache-Control");
        if (header == null) return directives;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            directives.put(kv[0].toLowerCase(), kv.length > 1 ? kv[1] : "");
        }
        return directives;
    }
}
