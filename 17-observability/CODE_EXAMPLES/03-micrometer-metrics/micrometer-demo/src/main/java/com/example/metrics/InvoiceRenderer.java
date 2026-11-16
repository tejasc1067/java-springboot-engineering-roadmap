package com.example.metrics;

import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

@Service
public class InvoiceRenderer {

    @Timed("invoice.render")   // needs management.observations.annotations.enabled=true
    public String render(String orderId) {
        return "<invoice " + orderId + ">";
    }
}
