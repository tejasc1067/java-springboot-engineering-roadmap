package com.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Three libraries are exercised here:
 *   JUnit Jupiter    @Test
 *   AssertJ          assertThat(...).isTrue() / .isFalse()
 *   Mockito          mock(...), when(...).thenReturn(...), any()
 *
 * None of them appear in pom.xml. All three came from spring-boot-starter-test.
 */
class RetryPolicyTest {

    @Test
    void retriesTransientFailuresWhileAttemptsRemain() {
        ExceptionClassifier classifier = mock(ExceptionClassifier.class);
        when(classifier.isTransient(any())).thenReturn(true);

        RetryPolicy policy = new RetryPolicy(classifier, 3);

        assertThat(policy.shouldRetry(0, new IOException("connect timeout"))).isTrue();
        assertThat(policy.shouldRetry(2, new IOException("connect timeout"))).isTrue();
        assertThat(policy.shouldRetry(3, new IOException("connect timeout"))).isFalse();
    }

    @Test
    void doesNotRetryNonTransientFailures() {
        ExceptionClassifier classifier = mock(ExceptionClassifier.class);
        when(classifier.isTransient(any())).thenReturn(false);

        RetryPolicy policy = new RetryPolicy(classifier, 5);

        assertThat(policy.shouldRetry(0, new IllegalStateException("bad input"))).isFalse();
    }
}
