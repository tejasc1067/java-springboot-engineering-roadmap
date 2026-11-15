package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// Real Tomcat, so the request goes Tomcat -> filters -> servlet exactly as in production.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServletFlowTest {

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
    void rawServletAndControllerReturnTheSameJson() throws Exception {
        HttpResponse<String> raw = send("GET", "/raw/books/1");
        HttpResponse<String> mvc = send("GET", "/api/books/1");

        assertThat(raw.statusCode()).isEqualTo(200);
        assertThat(raw.body()).isEqualTo(mvc.body());
    }

    @Test
    void filterRunsBeforeEveryServlet() throws Exception {
        assertThat(send("GET", "/raw/books/1").headers().firstValue("X-Passed-Filter")).hasValue("true");
        assertThat(send("GET", "/api/books/1").headers().firstValue("X-Passed-Filter")).hasValue("true");
    }

    @Test
    void rawServletOnlyHandlesWhatYouCodedByHand() throws Exception {
        // Bad id: the servlet's own hand-written check.
        assertThat(send("GET", "/raw/books/abc").statusCode()).isEqualTo(400);
        // No doPost override: HttpServlet's default answers 405.
        assertThat(send("POST", "/raw/books/1").statusCode()).isEqualTo(405);
    }
}
