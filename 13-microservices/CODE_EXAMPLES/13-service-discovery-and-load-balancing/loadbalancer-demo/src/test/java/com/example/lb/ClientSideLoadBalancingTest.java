package com.example.lb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client-side load balancing: the caller holds the list of live instances for a service
 * (in production that list comes from discovery, e.g. Eureka) and spreads calls across
 * them itself — no separate load-balancer box in the middle. Here we hand
 * RoundRobinLoadBalancer two instances of "inventory-service" and show that repeated
 * choices rotate across both.
 */
class ClientSideLoadBalancingTest {

    @Test
    void roundRobinSpreadsCallsAcrossAllInstances() {
        // Two live instances of inventory-service on different ports — what a registry
        // would report. In the scaffold (topic 05) this was a single hardcoded URL.
        List<ServiceInstance> instances = List.of(
                new DefaultServiceInstance("inventory-1", "inventory-service", "localhost", 8081, false),
                new DefaultServiceInstance("inventory-2", "inventory-service", "localhost", 8082, false));

        RoundRobinLoadBalancer loadBalancer =
                new RoundRobinLoadBalancer(providerOf(supplierFor("inventory-service", instances)),
                        "inventory-service");

        Set<Integer> chosenPorts = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            Response<ServiceInstance> response = Mono.from(loadBalancer.choose()).block();
            assertThat(response).isNotNull();
            assertThat(response.hasServer()).isTrue();
            chosenPorts.add(response.getServer().getPort());
        }

        // Both instances were used — the load was spread, not pinned to one.
        assertThat(chosenPorts).containsExactlyInAnyOrder(8081, 8082);
    }

    @Test
    void resolvesAServiceNameToAConcreteInstance() {
        List<ServiceInstance> instances = List.of(
                new DefaultServiceInstance("inventory-1", "inventory-service", "10.0.0.5", 8081, false));
        RoundRobinLoadBalancer loadBalancer =
                new RoundRobinLoadBalancer(providerOf(supplierFor("inventory-service", instances)),
                        "inventory-service");

        ServiceInstance chosen = Mono.from(loadBalancer.choose()).block().getServer();

        // "inventory-service" (a logical name) resolved to a real host:port you can call.
        // That indirection is what lets instances come and go without changing callers.
        assertThat(chosen.getHost()).isEqualTo("10.0.0.5");
        assertThat(chosen.getUri().toString()).isEqualTo("http://10.0.0.5:8081");
    }

    private static ServiceInstanceListSupplier supplierFor(String serviceId, List<ServiceInstance> instances) {
        return new ServiceInstanceListSupplier() {
            @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public Flux<List<ServiceInstance>> get() {
                return Flux.just(instances);
            }
        };
    }

    private static ObjectProvider<ServiceInstanceListSupplier> providerOf(ServiceInstanceListSupplier supplier) {
        return new ObjectProvider<>() {
            @Override
            public ServiceInstanceListSupplier getObject() {
                return supplier;
            }

            @Override
            public ServiceInstanceListSupplier getObject(Object... args) {
                return supplier;
            }

            @Override
            public ServiceInstanceListSupplier getIfAvailable() {
                return supplier;
            }

            @Override
            public ServiceInstanceListSupplier getIfUnique() {
                return supplier;
            }
        };
    }
}
