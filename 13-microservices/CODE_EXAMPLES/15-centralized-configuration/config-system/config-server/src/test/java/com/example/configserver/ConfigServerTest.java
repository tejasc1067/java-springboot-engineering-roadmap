package com.example.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the config server and asks it — over HTTP, exactly as a client would — for the
 * configuration of a service named "inventory-service". Proves the server serves
 * centralized config for a named application without that config living in the client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void servesCentralizedConfigForANamedService() {
        // {application}/{profile} is the config server's contract.
        String body = rest.getForObject(
                "http://localhost:" + port + "/inventory-service/default", String.class);

        assertThat(body).contains("config served centrally by Spring Cloud Config");
        assertThat(body).contains("inventory.low-stock-threshold");
    }
}
