package com.example;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceCalculator {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("10.00");

    // Hardcoded for now so this class can be scanned (Spring cannot supply a primitive
    // double from the container). Topic 08 (@Value) shows how to inject this from a
    // properties file. If we needed it externalized today we'd register PriceCalculator
    // with an @Bean method instead and pass the rate in by hand.
    private final BigDecimal taxRate = new BigDecimal("0.10");

    public BigDecimal total(List<String> items) {
        BigDecimal subtotal = UNIT_PRICE.multiply(BigDecimal.valueOf(items.size()));
        BigDecimal tax = subtotal.multiply(taxRate);
        return subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);
    }
}
