package com.example.async;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

// Module 07 topic 11: listeners run on the publisher's thread by default. @Async moves this one off it,
// so a slow mail server no longer slows down placing an order.
@Component
public class ConfirmationEmailListener {

    // Private field + getter on purpose: this bean is injected as a proxy (because of @Async), and reading
    // a field on the proxy object returns the proxy's own, never-initialised field. Methods are forwarded.
    private final CompletableFuture<String> ranOn = new CompletableFuture<>();

    public CompletableFuture<String> ranOn() { return ranOn; }

    @Async
    @EventListener
    public void onOrderPlaced(OrderPlaced event) {
        ranOn.complete(Thread.currentThread().getName());
    }
}
