package com.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// PITFALL 5 — extending ResponseEntityExceptionHandler is what lets us reshape the
// framework's OWN errors (malformed JSON, 405, body validation) into the same envelope
// as our domain errors. Drop the `extends` and handleMethodArgumentNotValid below stops
// overriding anything — body-validation failures revert to Spring's default shape and the
// contract becomes inconsistent.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // BODY validation: @Valid @RequestBody failure -> MethodArgumentNotValidException.
    // This is a framework hook, so we override it rather than use @ExceptionHandler.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        body.setTitle("Validation failed");
        body.setProperty("timestamp", Instant.now());
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                                  "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        body.setProperty("fieldErrors", fieldErrors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    // PARAM validation: @Validated controller + constraint on @PathVariable/@RequestParam ->
    // ConstraintViolationException. PITFALL 6 — a different exception from the body case;
    // handling only one leaves the other returning an ugly default. Handle both here, same shape.
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Validation failed", request);
        List<Map<String, String>> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return Map.of("field", field, "message", v.getMessage());
                })
                .toList();
        p.setProperty("fieldErrors", fieldErrors);
        return p;
    }

    // Fallback. The most general handler — Spring uses it only when nothing more specific
    // matches (most-specific-wins, pitfall 4). It ALWAYS logs the cause and returns a real
    // 500 with a generic body — never ex.getMessage() (pitfall 7).
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An unexpected error occurred.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
