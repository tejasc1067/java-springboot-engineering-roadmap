package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisambiguationTest {

    @Test
    void primaryResolvesUnqualifiedInjection() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            NotificationSender sender = ctx.getBean(NotificationSender.class);

            assertThat(sender.resolvedNotifier()).isInstanceOf(ConsoleEmailNotifier.class);
        }
    }

    @Test
    void qualifierResolvesToTheNamedBeanEvenWhenAPrimaryExists() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            LoggingNotificationSender sender = ctx.getBean(LoggingNotificationSender.class);

            assertThat(sender.resolvedNotifier()).isInstanceOf(LoggingNotifier.class);
        }
    }

    @Test
    void listInjectionGetsEveryBeanOfTheType() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            BroadcastSender sender = ctx.getBean(BroadcastSender.class);

            assertThat(sender.resolvedNotifiers())
                    .hasSize(2)
                    .anyMatch(n -> n instanceof ConsoleEmailNotifier)
                    .anyMatch(n -> n instanceof LoggingNotifier);
        }
    }

    // The failure mode: two beans match, no @Primary, no @Qualifier, single injection.
    // The context fails to refresh because NeedsOne's constructor cannot be resolved.
    @Test
    void unqualifiedInjectionWithTwoMatchesFailsAtStartup() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(BrokenConfig.class))
                .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
    }

    @Configuration
    static class BrokenConfig {
        // Two synthetic EmailNotifier beans; neither is @Primary.
        @Bean
        EmailNotifier alphaNotifier() { return (to, msg) -> { }; }

        @Bean
        EmailNotifier betaNotifier() { return (to, msg) -> { }; }

        // A bean that asks for ONE EmailNotifier. Spring cannot decide.
        @Bean
        NeedsOne needsOne(EmailNotifier notifier) { return new NeedsOne(notifier); }
    }

    static class NeedsOne {
        NeedsOne(EmailNotifier ignored) {
        }
    }
}
