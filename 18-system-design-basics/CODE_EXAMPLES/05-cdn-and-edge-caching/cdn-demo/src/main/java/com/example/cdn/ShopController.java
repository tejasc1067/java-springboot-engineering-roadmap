package com.example.cdn;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class ShopController {

    /** How many times the app actually built a response body. A CDN's job is to keep this low. */
    public final AtomicInteger renders = new AtomicInteger();

    private int productVersion = 1;

    // Public catalogue data: same for everyone, fine to share at the edge.
    // Browsers keep it 60 s; shared caches (the CDN) keep it 5 minutes.
    @GetMapping("/products/{id}")
    ResponseEntity<String> product(@PathVariable long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).sMaxAge(Duration.ofMinutes(5)).cachePublic())
                .body(render("{\"id\":" + id + ",\"name\":\"Kettle\",\"version\":" + productVersion + "}"));
    }

    // Broken: personal data with a plain max-age. "Not public" doesn't mean "private":
    // without `private` or `no-store`, a shared cache may store it and give it to the next user.
    @GetMapping("/broken/account")
    ResponseEntity<String> accountBroken(@CookieValue("session") String user) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))
                .body(render("{\"user\":\"" + user + "\",\"address\":\"" + user + "'s address\"}"));
    }

    @GetMapping("/account")
    ResponseEntity<String> account(@CookieValue("session") String user) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .body(render("{\"user\":\"" + user + "\",\"address\":\"" + user + "'s address\"}"));
    }

    // Broken: two representations of one URL, chosen by the Accept header, and no Vary.
    @GetMapping("/broken/prices/{id}")
    ResponseEntity<String> priceBroken(@PathVariable long id, @RequestHeader(value = "Accept", defaultValue = "") String accept) {
        return price(id, accept, CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic(), false);
    }

    @GetMapping("/prices/{id}")
    ResponseEntity<String> priceFixed(@PathVariable long id, @RequestHeader(value = "Accept", defaultValue = "") String accept) {
        return price(id, accept, CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic(), true);
    }

    private ResponseEntity<String> price(long id, String accept, CacheControl cacheControl, boolean vary) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().cacheControl(cacheControl);
        if (vary) builder.varyBy("Accept");
        if (accept.contains("text/csv")) {
            return builder.contentType(MediaType.parseMediaType("text/csv")).body(render("id,price\n" + id + ",1999\n"));
        }
        return builder.contentType(MediaType.APPLICATION_JSON).body(render("{\"id\":" + id + ",\"price\":1999}"));
    }

    // ShallowEtagHeaderFilter (on /reports/*) hashes the body after this method has run.
    // A matching If-None-Match gets a 304 with no body, but the report was still built.
    @GetMapping("/reports/{id}")
    ResponseEntity<String> report(@PathVariable long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())   // "store it, but check with me before every use"
                .body(render("report " + id + ": " + "x".repeat(10_000)));
    }

    // A "deep" ETag from a version number: checkNotModified answers 304 before any work is done.
    @GetMapping("/catalog/{id}")
    ResponseEntity<String> catalog(@PathVariable long id, WebRequest request) {
        String etag = "\"v" + productVersion + "\"";
        if (request.checkNotModified(etag)) return null;   // Spring has already set 304 and the ETag
        return ResponseEntity.ok()
                .eTag(etag)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noCache())
                .body(render("{\"id\":" + id + ",\"version\":" + productVersion + "}"));
    }

    public void changeProduct() { productVersion++; }

    public void reset() { productVersion = 1; renders.set(0); }

    private String render(String body) {
        renders.incrementAndGet();
        return body;
    }
}
