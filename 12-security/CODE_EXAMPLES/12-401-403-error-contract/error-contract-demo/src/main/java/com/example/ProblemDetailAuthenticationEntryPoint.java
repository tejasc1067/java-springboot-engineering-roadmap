package com.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// The AuthenticationEntryPoint is what ExceptionTranslationFilter calls when an UNAUTHENTICATED caller hits a
// protected route (401). It runs in the filter chain, so @RestControllerAdvice cannot reach it — this class is
// how we get a 401 into the ProblemDetail contract.
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    ProblemDetailAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 401 = we don't know who you are. Keep the detail generic — don't leak whether the user exists.
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Authentication is required to access this resource.");
    }
}
