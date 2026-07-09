package com.example.propagation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * The propagation mechanism: order-service copies the CALLER's bearer token onto its
 * OUTBOUND call to inventory-service, so the caller's identity travels across the hop.
 *
 * The token source is a Supplier so it's testable in isolation. In a real order-service
 * it reads the current request's credential — e.g. from Spring Security's
 * SecurityContextHolder (the Jwt token value) or the inbound Authorization header — so
 * the identity established at the edge (module 12) flows inward automatically.
 */
public class BearerTokenPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final Supplier<String> callerAuthorizationHeader;

    public BearerTokenPropagationInterceptor(Supplier<String> callerAuthorizationHeader) {
        this.callerAuthorizationHeader = callerAuthorizationHeader;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String authorization = callerAuthorizationHeader.get();
        if (authorization != null && !authorization.isBlank()) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
        }
        // If there's no caller token, we send the request WITHOUT one — and the secured
        // downstream will (correctly) reject it. We never fabricate identity.
        return execution.execute(request, body);
    }
}
