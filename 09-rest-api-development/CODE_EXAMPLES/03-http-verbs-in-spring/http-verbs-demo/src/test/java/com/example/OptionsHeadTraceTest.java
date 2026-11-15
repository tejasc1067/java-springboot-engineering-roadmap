package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// A real embedded Tomcat, not MockMvc: TRACE is blocked by Tomcat's connector,
// which MockMvc never goes through.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OptionsHeadTraceTest {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void optionsListsTheVerbsThisPathSupports() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/api/books/1");

        assertThat(response.statusCode()).isEqualTo(200);
        // Spring builds Allow from the mappings; nobody wrote an OPTIONS handler.
        // HEAD comes free with GET, OPTIONS with any mapping.
        assertThat(response.headers().firstValue("Allow").orElseThrow())
                .contains("GET", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS")
                .doesNotContain("POST");
    }

    @Test
    void headRunsTheGetHandlerButSendsNoBody() throws Exception {
        HttpResponse<String> get = send("GET", "/api/books");
        HttpResponse<String> head = send("HEAD", "/api/books");

        assertThat(get.body()).isNotEmpty();
        assertThat(head.statusCode()).isEqualTo(get.statusCode()).isEqualTo(200);
        assertThat(head.headers().firstValue("Content-Type"))
                .isEqualTo(get.headers().firstValue("Content-Type"));
        assertThat(head.body()).isEmpty();
    }

    @Test
    void unmappedVerbOnAMappedPathIs405WithAllowHeader() throws Exception {
        HttpResponse<String> response = send("POST", "/api/books/1");

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow").orElseThrow()).doesNotContain("POST");
    }

    @Test
    void traceIsRejectedByTomcatBeforeSpringSeesIt() throws Exception {
        HttpResponse<String> response = send("TRACE", "/api/books");

        // TRACE echoes the request back, including Cookie and Authorization
        // headers - the basis of Cross-Site Tracing. Tomcat's allowTrace=false.
        assertThat(response.statusCode()).isEqualTo(405);
    }
}
