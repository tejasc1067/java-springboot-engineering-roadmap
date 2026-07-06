package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// The point of this demo is the DEFAULT lock: we added spring-boot-starter-security and wrote no security
// configuration, yet every endpoint now requires authentication. The running app prints a *random*
// generated password, which a test can't read, so here we pin a known user via properties and prove the
// three outcomes an API client sees. TestRestTemplate exercises the full servlet + filter stack, so the
// 401s come from Spring Security's actual filter chain, not a mock.
//
// Note the explicit "Accept: application/json". The default chain picks its response by content type: a
// JSON/API client gets a 401 (below), but a browser (Accept: text/html) is instead *redirected to a login
// form*. We assert the API-client behavior because that's what this module's callers are.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.user.name=demo",
                "spring.security.user.password=demo"
        })
class DefaultSecurityTest {

    @Autowired
    TestRestTemplate rest;

    private HttpEntity<Void> asJsonClient() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    @Test
    void anonymousRequestIsBlockedWith401() {
        // No credentials at all: the filter chain rejects before the request ever reaches BookController.
        ResponseEntity<String> response =
                rest.exchange("/api/books", HttpMethod.GET, asJsonClient(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPasswordIsBlockedWith401() {
        ResponseEntity<String> response = rest.withBasicAuth("demo", "wrong")
                .exchange("/api/books", HttpMethod.GET, asJsonClient(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validCredentialsGet200() {
        // Correct HTTP Basic credentials authenticate, so the request reaches the controller and returns data.
        ResponseEntity<String> response = rest.withBasicAuth("demo", "demo")
                .exchange("/api/books", HttpMethod.GET, asJsonClient(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
