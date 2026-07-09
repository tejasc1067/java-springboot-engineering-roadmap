package com.example.tracing;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter in isolation: generate an id when absent, reuse one when present, expose it
 * in the MDC during the request, and clear the MDC afterwards.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenTheRequestHasNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> idDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(idDuringRequest.get()).isNotBlank();                                  // set during request
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(idDuringRequest.get());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();                        // cleared afterwards
    }

    @Test
    void reusesTheIdTheRequestAlreadyCarries() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "upstream-id-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> idDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(idDuringRequest.get()).isEqualTo("upstream-id-42");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("upstream-id-42");
    }
}
