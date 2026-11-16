package com.example.logcorrelation;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
abstract class LogTestBase {

    @LocalServerPort
    int port;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
    }

    record Reply(int status, String traceIdHeader, String body) { }

    Reply get(String path) {
        return http.get().uri(path).exchange((request, response) -> new Reply(
                response.getStatusCode().value(),
                response.getHeaders().getFirst("X-Trace-Id"),
                new String(response.getBody().readAllBytes())));
    }

    static String lineContaining(CapturedOutput output, String text) {
        return output.getOut().lines().filter(line -> line.contains(text)).reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("no log line containing: " + text));
    }

    static Map<String, Object> json(String line) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(line, Map.class);
    }
}
