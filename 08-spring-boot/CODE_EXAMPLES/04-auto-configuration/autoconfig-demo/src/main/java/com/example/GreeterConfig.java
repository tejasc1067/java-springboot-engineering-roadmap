package com.example;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mirrors the conditional pattern Spring Boot's own auto-configurations use:
 *   - opt-in implementation via @ConditionalOnProperty
 *   - fallback implementation via @ConditionalOnMissingBean
 *
 * The order matters: the property-gated bean is evaluated first. If it
 * registers, @ConditionalOnMissingBean on the fallback sees a Greeter is
 * already present and skips registration.
 */
@Configuration
public class GreeterConfig {

    @Bean
    @ConditionalOnProperty(name = "greeter.style", havingValue = "fancy")
    public Greeter fancyGreeter() {
        return new FancyGreeter();
    }

    @Bean
    @ConditionalOnMissingBean(Greeter.class)
    public Greeter plainGreeter() {
        return new PlainGreeter();
    }
}
