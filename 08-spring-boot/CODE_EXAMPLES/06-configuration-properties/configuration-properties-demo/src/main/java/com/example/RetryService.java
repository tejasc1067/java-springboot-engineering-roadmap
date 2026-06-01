package com.example;

import org.springframework.stereotype.Component;

@Component
public class RetryService {

    private final RetryProperties config;

    public RetryService(RetryProperties config) {
        this.config = config;
    }

    public RetryProperties config() {
        return config;
    }

    public String describePolicy() {
        return "max=" + config.maxAttempts()
                + " backoff=" + config.backoff().toMillis() + "ms"
                + " notify=" + config.notification().email()
                + " enabled=" + config.notification().enabled();
    }
}
