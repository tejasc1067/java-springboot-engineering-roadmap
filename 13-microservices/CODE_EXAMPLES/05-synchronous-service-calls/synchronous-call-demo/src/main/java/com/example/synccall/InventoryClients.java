package com.example.synccall;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Two ways to build the same synchronous call, both backed by RestClient.
 * In the scaffold this lives in a @Configuration/@Bean; here it is a plain factory
 * so the test can point it at a stub server on any port.
 */
public final class InventoryClients {

    private InventoryClients() {
    }

    /** The declarative HTTP interface (recommended): describe once, call as a method. */
    public static InventoryClient declarative(String baseUrl) {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(InventoryClient.class);
    }

    /** The plain RestClient equivalent (fine for a one-off call): build the request inline. */
    public static StockResponse getStockInline(String baseUrl, String sku) {
        return RestClient.create()
                .get()
                .uri(baseUrl + "/api/inventory/{sku}", sku)
                .retrieve()
                .body(StockResponse.class);
    }
}
