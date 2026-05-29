package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPolicyTest {

    @Test
    void valuesAreLoadedFromTheFile() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PricingPolicy policy = ctx.getBean(PricingPolicy.class);

            assertThat(policy.taxRate()).isEqualByComparingTo(new BigDecimal("0.10"));
            assertThat(policy.unitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(policy.appName()).isEqualTo("PricingApp");
        }
    }

    @Test
    void missingPropertyUsesProvidedDefault() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PricingPolicy policy = ctx.getBean(PricingPolicy.class);

            // pricing.max.items.per.order is not in application.properties; the @Value
            // default of 100 supplied it.
            assertThat(policy.maxItemsPerOrder()).isEqualTo(100);
        }
    }

    @Test
    void systemPropertyOverridesPropertySource() {
        System.setProperty("pricing.app.name", "SystemOverride");
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PricingPolicy policy = ctx.getBean(PricingPolicy.class);

            assertThat(policy.appName()).isEqualTo("SystemOverride");
        } finally {
            System.clearProperty("pricing.app.name");
        }
    }
}
