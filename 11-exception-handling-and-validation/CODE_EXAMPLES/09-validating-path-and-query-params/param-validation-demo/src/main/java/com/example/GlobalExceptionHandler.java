package com.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    // BODY validation: @Valid @RequestBody failures arrive as MethodArgumentNotValidException, which
    // IS a Spring MVC exception, so ResponseEntityExceptionHandler already has a hook — we override it
    // to add the fieldErrors array (the default body has none).
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

    // PARAM validation: a failed @Min/@Max on a @PathVariable/@RequestParam (under class-level @Validated)
    // throws ConstraintViolationException. That is NOT a Spring MVC exception — it comes from the bean
    // validation layer (jakarta.validation), so ResponseEntityExceptionHandler has no hook for it and it
    // would otherwise fall through to a generic 500. We register an explicit @ExceptionHandler and map it
    // into the SAME fieldErrors contract as the body case above.
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Validation failed", request);
        List<Map<String, String>> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> {
                    // propertyPath is like "one.id" or "page.size" — keep just the param name.
                    String path = v.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return Map.of("field", field, "message", v.getMessage());
                })
                .toList();
        p.setProperty("fieldErrors", fieldErrors);
        return p;
    }

    // One helper stamps every error with the same envelope: instance (path) + timestamp.
    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
