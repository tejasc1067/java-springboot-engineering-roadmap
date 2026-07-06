package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// End-to-end: TestRestTemplate drives the real filter chain, the auto-configured DaoAuthenticationProvider,
// our JpaUserDetailsService, and BCrypt password matching. No form login is configured, so an
// unauthenticated request is a clean 401 regardless of Accept header.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationModelTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AppUserRepository users;

    @Test
    void anonymousIsRejected() {
        ResponseEntity<String> response = rest.getForEntity("/api/books", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void seededDatabaseUserCanAuthenticate() {
        ResponseEntity<String> response = rest.withBasicAuth("alice", "password")
                .getForEntity("/api/books", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongPasswordIsRejected() {
        ResponseEntity<String> response = rest.withBasicAuth("alice", "wrong")
                .getForEntity("/api/books", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUserIsRejected() {
        ResponseEntity<String> response = rest.withBasicAuth("nobody", "password")
                .getForEntity("/api/books", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordIsStoredAsABcryptHashNotPlaintext() {
        // Foreshadows topic 05: the database never holds the raw password.
        AppUser alice = users.findByUsername("alice").orElseThrow();
        assertThat(alice.getPassword()).isNotEqualTo("password");
        assertThat(alice.getPassword()).startsWith("$2");   // BCrypt hashes begin with $2a/$2b/$2y
    }
}
