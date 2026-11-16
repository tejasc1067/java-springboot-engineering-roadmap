package com.example.logcorrelation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Only OrderNotFoundException is handled here. Other exceptions get Spring Boot's default error body,
// unless the catch-all profile is on (CatchAllErrorHandler).
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorHandler {

    private final Tracer tracer;

    public ErrorHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        Span span = tracer.currentSpan();
        // The id a customer can read out to support, and support can paste into the log search.
        problem.setProperty("traceId", span == null ? null : span.context().traceId());
        return problem;
    }
}
