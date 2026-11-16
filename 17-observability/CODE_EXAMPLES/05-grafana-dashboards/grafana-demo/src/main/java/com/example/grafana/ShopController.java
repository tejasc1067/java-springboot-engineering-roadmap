package com.example.grafana;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class ShopController {

    private final JdbcTemplate jdbc;
    private final AtomicLong checkouts = new AtomicLong();

    public ShopController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fast, uses the connection pool. Id "boom" fails with a 500 so the dashboard has errors to show. */
    @GetMapping("/api/products/{id}")
    public ResponseEntity<String> product(@PathVariable String id) {
        if (id.equals("boom")) throw new IllegalStateException("product service failed");
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        return ResponseEntity.ok("product " + id + " " + one);
    }

    /** Usually 20 ms. Every 20th call takes 1.5 s: 5% slow, the tail an average hides. */
    @GetMapping("/api/checkout")
    public String checkout() throws InterruptedException {
        Thread.sleep(checkouts.incrementAndGet() % 20 == 0 ? 1500 : 20);
        return "paid";
    }
}
