package com.example;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private final Counter greetingsSent;

    // Register the meter once; incrementing it is then just an atomic add.
    public GreetingController(MeterRegistry registry) {
        this.greetingsSent = Counter.builder("greetings.sent")
                .description("Greetings returned to callers")
                .register(registry);
    }

    @GetMapping("/hello")
    public String hello() {
        greetingsSent.increment();
        return "hello";
    }
}
