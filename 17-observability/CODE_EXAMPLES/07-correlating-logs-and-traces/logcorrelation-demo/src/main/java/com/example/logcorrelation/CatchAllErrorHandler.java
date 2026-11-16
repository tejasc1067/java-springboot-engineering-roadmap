package com.example.logcorrelation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Handles every exception nobody else handled, while the request's span is still current:
// the error is logged with the trace id, and the client gets the id in the body.
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Profile("catch-all")
public class CatchAllErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CatchAllErrorHandler.class);

    private final Tracer tracer;

    public CatchAllErrorHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("request failed", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Quote the traceId when contacting support.");   // no internals to the client
        Span span = tracer.currentSpan();
        problem.setProperty("traceId", span == null ? null : span.context().traceId());
        return problem;
    }
}
