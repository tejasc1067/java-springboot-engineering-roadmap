package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

// Filter-level security errors are NOT @ResponseBody return values — nothing serializes them for us. So this
// helper builds the SAME ProblemDetail envelope module 11 used (title, detail, instance, timestamp) and writes
// it to the raw response by hand. This is the seam that makes a security 401/403 look identical to a 404 from
// the @RestControllerAdvice.
@Component
public class ProblemDetailWriter {

    // MUST be the Spring-configured ObjectMapper, not new ObjectMapper(): Boot registers a ProblemDetail
    // Jackson mixin (so type/title/status/detail/instance and custom properties serialize flat) and the
    // JavaTimeModule (so the Instant timestamp becomes an ISO string). A hand-built mapper produces the wrong
    // JSON shape — see COMMON_MISTAKES.
    private final ObjectMapper objectMapper;

    ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title,
               String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        // RFC 7807 media type — the same one Spring uses for ProblemDetail responses from the advice.
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // getWriter() defaults to ISO-8859-1; force UTF-8 so the body (and any non-ASCII detail) matches the
        // advice's output exactly. Set AFTER the content type, which would otherwise reset the charset.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
