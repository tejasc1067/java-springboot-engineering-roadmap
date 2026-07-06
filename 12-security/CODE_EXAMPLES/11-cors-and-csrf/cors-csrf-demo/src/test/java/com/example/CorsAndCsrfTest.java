package com.example;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsAndCsrfTest {

    @Autowired
    MockMvc mvc;

    // ---------- CSRF ----------
    // The lesson: state-changing requests need a CSRF token; reads don't. On a cookie/session app this is what
    // stops a forged cross-site POST that rides the victim's session cookie.

    @Test
    void getIsNotCsrfProtected() throws Exception {
        // Reads are safe by definition, so no token is required.
        mvc.perform(get("/api/whoami").with(user("alice")))
                .andExpect(status().isOk());
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        // Authenticated, but no CSRF token -> 403. A cross-site forgery lands exactly here: it can send the
        // cookie but cannot supply the token.
        mvc.perform(post("/api/transfer").with(user("alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrfTokenSucceeds() throws Exception {
        // The legitimate SPA reads the XSRF-TOKEN cookie and echoes it; csrf() simulates supplying that token.
        mvc.perform(post("/api/transfer").with(user("alice")).with(csrf()))
                .andExpect(status().isOk());
    }

    // ---------- CORS ----------
    // The lesson: CORS decides which OTHER browser origins may read this API. The server just emits
    // Access-Control-Allow-* headers; the browser enforces them.

    @Test
    void corsPreflightFromAllowedOriginIsApproved() throws Exception {
        // The browser sends this OPTIONS preflight before a cross-origin POST. The allowed origin is echoed
        // back, so the browser will then let the real request proceed. Preflight needs no authentication.
        mvc.perform(options("/api/transfer")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void corsPreflightFromDisallowedOriginIsRejected() throws Exception {
        // An origin that isn't in the allow-list is refused: no Access-Control-Allow-Origin header is emitted,
        // so the browser blocks the SPA from reading anything. Spring returns 403 for the invalid preflight.
        mvc.perform(options("/api/transfer")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsActualRequestGetsAllowOriginHeader() throws Exception {
        // On the real (non-preflight) cross-origin GET, the allow-origin header tells the browser the SPA may
        // read the response body.
        mvc.perform(get("/api/whoami").header("Origin", "http://localhost:3000").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
