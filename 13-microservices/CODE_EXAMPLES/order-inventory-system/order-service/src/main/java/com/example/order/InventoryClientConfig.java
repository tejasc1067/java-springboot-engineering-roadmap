package com.example.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Turns the InventoryClient interface into a live bean backed by a RestClient.
 * The base URL is externalized (inventory.base-url) rather than hardcoded — that
 * single indirection is what lets topic 13 swap a fixed host:port for a service
 * name resolved through discovery, without touching the interface or callers.
 */
@Configuration
public class InventoryClientConfig {

    @Bean
    public InventoryClient inventoryClient(@Value("${inventory.base-url}") String baseUrl) {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(InventoryClient.class);
    }
}
