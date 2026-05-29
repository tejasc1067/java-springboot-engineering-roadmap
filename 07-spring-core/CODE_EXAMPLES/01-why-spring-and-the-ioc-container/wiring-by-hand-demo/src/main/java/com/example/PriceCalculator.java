package com.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PriceCalculator {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("10.00");

    private final BigDecimal taxRate;

    public PriceCalculator(double taxRate) {
        this.taxRate = BigDecimal.valueOf(taxRate);
    }

    public BigDecimal total(List<String> items) {
        BigDecimal subtotal = UNIT_PRICE.multiply(BigDecimal.valueOf(items.size()));
        BigDecimal tax = subtotal.multiply(taxRate);
        return subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);
    }
}
