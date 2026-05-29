package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PricingPolicy policy = context.getBean(PricingPolicy.class);

            System.out.println("appName            = " + policy.appName());
            System.out.println("taxRate            = " + policy.taxRate());
            System.out.println("unitPrice          = " + policy.unitPrice());
            System.out.println("maxItemsPerOrder   = " + policy.maxItemsPerOrder() + "  (default; not in file)");
        }
    }
}
