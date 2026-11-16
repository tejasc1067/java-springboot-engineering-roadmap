package com.example.k8s;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestTest {

    @Test
    void brokenManifests_haveFourBugsKubectlWouldAccept() throws Exception {
        List<String> problems = ManifestChecks.problemsIn(Path.of("broken"));

        problems.forEach(System.out::println);
        assertThat(problems).containsExactlyInAnyOrder(
                "Service orders selects {app=order-service} but no pod has those labels",
                "Service orders sends to targetPort 8081 but the container listens on [8080]",
                "Deployment orders readinessProbe path /health/readiness is not a Spring Boot health endpoint",
                "ConfigMap orders-config holds SPRING_DATASOURCE_PASSWORD - secrets belong in a Secret");
    }

    @Test
    void fixedManifests_passEveryCheck() throws Exception {
        assertThat(ManifestChecks.problemsIn(Path.of("fixed"))).isEmpty();
    }
}
