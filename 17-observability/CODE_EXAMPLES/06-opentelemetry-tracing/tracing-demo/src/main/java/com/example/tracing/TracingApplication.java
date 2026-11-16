package com.example.tracing;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableAsync
public class TracingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TracingApplication.class, args);
    }

    // Any SpanExporter bean receives finished spans. In production this would be an OTLP exporter
    // (added by depending on opentelemetry-exporter-otlp and setting management.otlp.tracing.endpoint).
    @Bean
    InMemorySpanExporter spanExporter() {
        return InMemorySpanExporter.create();
    }

    // Built from Boot's RestClient.Builder: observation is wired in, so outgoing calls get a span and a traceparent header.
    @Bean
    RestClient tracedClient(RestClient.Builder builder) {
        return builder.build();
    }

    // Built by hand: nobody wires observation in, so the trace stops here.
    @Bean
    RestClient untracedClient() {
        return RestClient.create();
    }

    // Boot applies a TaskDecorator bean to its @Async executor. This one copies the caller's
    // trace context (and MDC) onto the worker thread.
    @Bean
    @ConditionalOnProperty(name = "demo.async-context-propagation", havingValue = "true", matchIfMissing = true)
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
