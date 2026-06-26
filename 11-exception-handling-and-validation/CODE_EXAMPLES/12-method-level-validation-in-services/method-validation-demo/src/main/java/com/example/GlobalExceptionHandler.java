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

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // BODY validation failure (controller's @Valid @RequestBody). This is genuine USER input ->
    // 400, with the per-field details surfaced (topics 08/09).
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

    // METHOD-LEVEL validation failure from a @Validated SERVICE bean. The controller already
    // validated the user's input, so reaching this means a CALLER passed bad args — a programming
    // error, not a client mistake. We therefore map it to 500 and keep the field detail OUT of the
    // body (it describes internal method parameters, not anything the client can fix). The full
    // detail goes to the log instead. (Contrast topic 09, where the SAME exception type comes from
    // a controller @PathVariable/@RequestParam — there it IS user input, so it's a 400.)
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleServiceConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.error("Service-layer constraint violation (a caller passed invalid args): {}", ex.getMessage());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An unexpected error occurred.", request);
    }

    // BUSINESS-RULE failure: well-formed request the domain can't satisfy. 409 Conflict, and the
    // detail is a message WE wrote, so it's safe to expose.
    @ExceptionHandler(InsufficientCopiesException.class)
    public ProblemDetail handleInsufficientCopies(InsufficientCopiesException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Insufficient copies", ex.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
