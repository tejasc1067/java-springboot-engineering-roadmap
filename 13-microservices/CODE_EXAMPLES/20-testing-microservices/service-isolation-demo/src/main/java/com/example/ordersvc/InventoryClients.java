package com.example.ordersvc;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Builds a real InventoryClient pointed at a base URL. In a Spring app this would be a @Bean;
 * here it is a plain factory so a test can aim the real client at a WireMock server on whatever
 * port WireMock happened to pick. The isolation test exercises THIS real client (URL, JSON
 * parsing, status handling) against a stub — it does not replace it with a mock.
 */
public final class InventoryClients {

    private InventoryClients() {
    }

    public static InventoryClient pointingAt(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(500));
        requestFactory.setReadTimeout(readTimeout); // topic 09: never leave an outbound call unbounded
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(InventoryClient.class);
    }
}
