package com.example.tracing;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

// Spring Boot tests switch tracing off unless @AutoConfigureObservability is present.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import(Spans.class)
abstract class TracingTestBase {

    @LocalServerPort
    int port;

    @Autowired
    Spans spans;

    // A plain client with no tracing, playing the part of a browser: each request starts a new trace.
    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        spans.clear();
    }
}
