package com.example.propagation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The propagation logic in isolation: copy the caller's token onto the outbound request
 * when there is one, and send nothing (never a fabricated identity) when there isn't.
 */
class PropagationInterceptorTest {

    @Test
    void copiesTheCallersTokenOntoTheOutboundRequest() throws IOException {
        var interceptor = new BearerTokenPropagationInterceptor(() -> "Bearer abc.def.ghi");
        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.GET, URI.create("http://inventory/api/inventory/SKU-BOOK"));

        interceptor.intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer abc.def.ghi");
    }

    @Test
    void sendsNoAuthorizationWhenThereIsNoCallerToken() throws IOException {
        var interceptor = new BearerTokenPropagationInterceptor(() -> null);
        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.GET, URI.create("http://inventory/api/inventory/SKU-BOOK"));

        interceptor.intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }
}
