package com.example.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "demo.async-context-propagation=false")
class AsyncNoPropagationTest extends TracingTestBase {

    @Test
    void withoutTheDecorator_theAsyncThreadHasNoTrace() {
        Map<?, ?> result = http.get().uri("/report").retrieve().body(Map.class);

        assertThat(result.get("traceId")).isNotNull();
        assertThat(result.get("asyncTraceId")).isEqualTo("none");
    }
}
