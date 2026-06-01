package com.example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Bound from properties under "app.retry.*" in application.yml.
 * Validation fires at startup; bad config causes the context to fail.
 *
 * The nested Notification record has @Valid on its outer field so the
 * constraints inside Notification are also checked (cascading validation).
 */
@ConfigurationProperties(prefix = "app.retry")
@Validated
public record RetryProperties(

        @Min(1)
        @Max(10)
        int maxAttempts,

        @NotNull
        Duration backoff,

        @NotNull
        @Valid
        Notification notification) {

    public record Notification(

            @NotBlank
            String email,

            boolean enabled) {
    }
}
