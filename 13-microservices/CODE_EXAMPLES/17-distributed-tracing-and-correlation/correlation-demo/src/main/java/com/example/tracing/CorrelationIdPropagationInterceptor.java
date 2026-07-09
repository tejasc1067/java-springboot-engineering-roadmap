package com.example.tracing;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Forwards the current request's correlation id onto every OUTBOUND call, so the
 * downstream service continues the same id instead of starting a new one. This is what
 * makes a single request show up as ONE id across every service it touches.
 *
 * The synchronous outbound call runs on the same thread that's serving the request, so
 * the id set in the MDC by CorrelationIdFilter is visible here.
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
