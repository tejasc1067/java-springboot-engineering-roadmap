package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockitoInDepthTest {

    @Mock
    MessageGateway gateway;

    @Captor
    ArgumentCaptor<String> messageCaptor;

    // --- argument matchers: stub/verify without pinning every exact argument ---

    @Test
    void matchersLetYouStubForAnyArgument() {
        when(gateway.send(anyString(), anyString())).thenReturn(true);

        new OrderNotifier(gateway).confirm("ada@example.com", "A-1");

        // Rule: if ANY argument uses a matcher, ALL must. So the exact email is wrapped
        // in eq(...) to sit alongside anyString().
        verify(gateway).send(eq("ada@example.com"), anyString());
    }

    // --- verifying how many times something was called ---

    @Test
    void verifyCanCountCalls() {
        when(gateway.send(anyString(), anyString())).thenReturn(true);

        int delivered = new OrderNotifier(gateway)
                .broadcast(List.of("a@x.com", "b@x.com", "c@x.com"), "Sale today");

        assertThat(delivered).isEqualTo(3);
        verify(gateway, times(3)).send(anyString(), eq("Sale today"));
        verify(gateway, never()).send(eq("nobody@x.com"), anyString());
    }

    // --- ArgumentCaptor: assert on WHAT was passed to the mock ---

    @Test
    void argumentCaptorRevealsTheExactArgument() {
        when(gateway.send(anyString(), anyString())).thenReturn(true);

        new OrderNotifier(gateway).confirm("ada@example.com", "A-42");

        // Capture the message argument, then assert on the string the notifier built.
        verify(gateway).send(eq("ada@example.com"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo("Order A-42 confirmed");
    }

    // --- thenThrow: make a dependency fail so you can test the error path ---

    @Test
    void thenThrowSimulatesADependencyFailure() {
        when(gateway.send(anyString(), anyString()))
                .thenThrow(new RuntimeException("SMTP server down"));

        assertThatThrownBy(() -> new OrderNotifier(gateway).confirm("ada@example.com", "A-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP server down");
    }

    // --- spy: a real object where you override just one method ---

    @Test
    void spyRunsRealCodeExceptWhereOverridden() {
        GreetingService service = spy(new GreetingService());

        // doReturn(...).when(spy).method() overrides prefix() only.
        // (Use doReturn here, not when(service.prefix()): the latter would call the real
        // prefix() while setting up the stub, which is what we're trying to avoid.)
        doReturn("Hi, ").when(service).prefix();

        assertThat(service.greet("Ada")).isEqualTo("Hi, Ada");   // greet() still runs for real
        verify(service).prefix();
    }
}
