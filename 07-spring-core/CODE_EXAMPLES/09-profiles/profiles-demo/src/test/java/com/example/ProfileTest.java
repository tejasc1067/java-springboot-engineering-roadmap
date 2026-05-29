package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileTest {

    private AnnotationConfigApplicationContext contextWithProfile(String... profiles) {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().setActiveProfiles(profiles);
        ctx.register(AppConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void devProfileWiresTheConsoleNotifier() {
        try (var ctx = contextWithProfile("dev")) {
            Mailer mailer = ctx.getBean(Mailer.class);

            assertThat(mailer.channel()).isEqualTo("console");
            assertThat(ctx.getBean(EmailNotifier.class)).isInstanceOf(ConsoleEmailNotifier.class);
        }
    }

    @Test
    void prodProfileWiresTheSmtpNotifier() {
        try (var ctx = contextWithProfile("prod")) {
            Mailer mailer = ctx.getBean(Mailer.class);

            assertThat(mailer.channel()).isEqualTo("smtp");
            assertThat(ctx.getBean(EmailNotifier.class)).isInstanceOf(SmtpEmailNotifier.class);
        }
    }

    @Test
    void noProfileMeansNoEmailNotifierAndTheContextFailsToStart() {
        assertThatThrownBy(() -> {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(AppConfig.class);
            ctx.refresh();
        }).isInstanceOf(UnsatisfiedDependencyException.class)
          .hasMessageContaining("EmailNotifier");
    }
}
