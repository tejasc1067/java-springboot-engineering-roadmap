package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PricingPolicy {

    private final BigDecimal taxRate;
    private final BigDecimal unitPrice;
    private final String appName;
    private final int maxItemsPerOrder;

    public PricingPolicy(
            @Value("${pricing.tax.rate}") BigDecimal taxRate,
            @Value("${pricing.unit.price}") BigDecimal unitPrice,
            @Value("${pricing.app.name:UnnamedApp}") String appName,
            @Value("${pricing.max.items.per.order:100}") int maxItemsPerOrder) {
        this.taxRate = taxRate;
        this.unitPrice = unitPrice;
        this.appName = appName;
        this.maxItemsPerOrder = maxItemsPerOrder;
    }

    public BigDecimal taxRate()        { return taxRate; }
    public BigDecimal unitPrice()      { return unitPrice; }
    public String appName()            { return appName; }
    public int maxItemsPerOrder()      { return maxItemsPerOrder; }
}
