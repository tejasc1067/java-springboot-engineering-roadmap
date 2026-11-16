package com.example.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AsyncPropagationTest extends TracingTestBase {

    @Test
    void withContextPropagatingTaskDecorator_theAsyncThreadIsInTheSameTrace() {
        Map<?, ?> result = http.get().uri("/report").retrieve().body(Map.class);

        assertThat(result.get("asyncTraceId")).isEqualTo(result.get("traceId"));
    }
}
