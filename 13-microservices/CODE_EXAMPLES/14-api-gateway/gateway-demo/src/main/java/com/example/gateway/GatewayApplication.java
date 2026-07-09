package com.example.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * A single entry point in front of the services. Clients call the gateway; the gateway
 * routes to the right internal service and applies edge concerns once, centrally.
 *
 * This one route says: anything under /inventory/** goes to inventory-service, with the
 * path rewritten (/inventory/X -> /api/inventory/X) and an X-Gateway header added on the
 * way through. Auth, CORS, and rate limiting would be added here too — in ONE place,
 * rather than duplicated in every downstream service.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> inventoryRoute(@Value("${inventory.uri}") String inventoryUri) {
        return route("inventory-route")
                .route(path("/inventory/**"), http(inventoryUri))
                .before(rewritePath("/inventory/(?<segment>.*)", "/api/inventory/${segment}"))
                .before(addRequestHeader("X-Gateway", "spring-cloud-gateway-mvc"))
                .build();
    }
}
