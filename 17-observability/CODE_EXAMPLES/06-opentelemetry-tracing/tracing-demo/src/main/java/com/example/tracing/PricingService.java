package com.example.tracing;

import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

    // Needs management.observations.annotations.enabled=true (and spring-boot-starter-aop).
    @Observed(name = "pricing.lookup", contextualName = "look-up-price")
    public long price(String sku) {
        return 1999;
    }
}
