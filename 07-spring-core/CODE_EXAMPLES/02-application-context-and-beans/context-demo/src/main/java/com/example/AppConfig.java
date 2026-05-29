package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @Configuration marks this class as a source of bean definitions. Spring will
// instantiate it, then call every @Bean method to populate the container.
@Configuration
public class AppConfig {

    @Bean
    public InventoryService inventoryService() {
        return new InventoryService();
    }

    @Bean
    public PriceCalculator priceCalculator() {
        return new PriceCalculator(0.10);
    }

    @Bean
    public EmailNotifier emailNotifier() {
        return new ConsoleEmailNotifier();
    }

    @Bean
    public OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    // The parameters here are NOT supplied by you. Spring sees the parameter types
    // and supplies the matching beans from the container. Order of @Bean methods in
    // this file does not matter -- Spring works out the dependency order itself.
    @Bean
    public OrderService orderService(InventoryService inventory,
                                     PriceCalculator priceCalculator,
                                     EmailNotifier emailNotifier,
                                     OrderRepository orderRepository) {
        return new OrderService(inventory, priceCalculator, emailNotifier, orderRepository);
    }
}
