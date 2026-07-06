package com.example;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// The module-11 error contract, unchanged. It catches exceptions thrown by CONTROLLERS (a missing book -> 404)
// and builds a ProblemDetail. Note what it does NOT catch: AuthenticationException and AccessDeniedException
// thrown in the SECURITY FILTER CHAIN never reach here, because ExceptionTranslationFilter handles them before
// the DispatcherServlet is ever invoked. That gap is exactly why the entry point / access-denied handler exist.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Book not found", ex.getMessage(), request);
    }

    // Identical envelope to ProblemDetailWriter: title + instance + timestamp. Keeping the two in sync is what
    // makes every error — validation, not-found, unauthorized, forbidden — one predictable shape for clients.
    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
