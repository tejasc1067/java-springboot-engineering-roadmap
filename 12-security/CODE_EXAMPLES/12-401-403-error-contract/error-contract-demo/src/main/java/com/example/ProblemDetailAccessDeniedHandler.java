package com.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// The AccessDeniedHandler is what ExceptionTranslationFilter calls when an AUTHENTICATED caller lacks the
// required authority (403). Same story as the entry point: it lives in the filter chain, so it — not the
// advice — is where a 403 must be turned into ProblemDetail. Using setStatus/write here (not sendError) also
// avoids the /error re-dispatch that turned topic 08's default 403 into a 401.
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    ProblemDetailAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to access this resource.");
    }
}
