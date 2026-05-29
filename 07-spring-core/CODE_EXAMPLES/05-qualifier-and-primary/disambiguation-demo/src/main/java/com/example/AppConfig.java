package com.example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

// The excludeFilter skips test-only nested @Configuration classes (DisambiguationTest$BrokenConfig)
// so the working AppConfig doesn't accidentally absorb the deliberately-broken scenario at test time.
// In a real app you would not normally need a filter here.
@Configuration
@ComponentScan(
        basePackages = "com.example",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = ".*BrokenConfig"
        )
)
public class AppConfig {
}
