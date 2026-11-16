package com.example.k8s;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Checks for mistakes kubectl accepts without complaint: every manifest below is valid YAML and a valid
 * Kubernetes object, yet the app would never receive a request. These are the checks a reviewer does by eye.
 */
@SuppressWarnings("unchecked")
class ManifestChecks {

    static List<String> problemsIn(Path dir) throws IOException {
        List<Map<String, Object>> docs = load(dir);
        List<Map<String, Object>> deployments = ofKind(docs, "Deployment");
        List<String> problems = new ArrayList<>();

        for (Map<String, Object> service : ofKind(docs, "Service")) {
            String name = name(service);
            Map<String, Object> spec = map(service, "spec");
            Map<String, Object> selector = map(spec, "selector");

            List<Map<String, Object>> matched = deployments.stream()
                    .filter(d -> podLabels(d).entrySet().containsAll(selector.entrySet()))
                    .toList();
            if (matched.isEmpty()) {
                problems.add("Service " + name + " selects " + selector + " but no pod has those labels");
                matched = deployments;   // still check the ports, against the pods it was probably meant for
            }
            for (Map<String, Object> port : list(spec, "ports")) {
                Object targetPort = port.get("targetPort");
                boolean exists = matched.stream().flatMap(d -> containerPorts(d).stream()).anyMatch(targetPort::equals);
                if (!exists) {
                    problems.add("Service " + name + " sends to targetPort " + targetPort + " but the container listens on "
                            + matched.stream().flatMap(d -> containerPorts(d).stream()).toList());
                }
            }
        }

        for (Map<String, Object> deployment : deployments) {
            for (Map<String, Object> container : containers(deployment)) {
                for (String probe : List.of("livenessProbe", "readinessProbe")) {
                    Map<String, Object> probeSpec = (Map<String, Object>) container.get(probe);
                    if (probeSpec == null) {
                        problems.add("Deployment " + name(deployment) + " has no " + probe);
                        continue;
                    }
                    String path = (String) map(probeSpec, "httpGet").get("path");
                    if (!path.startsWith("/actuator/health/")) {
                        problems.add("Deployment " + name(deployment) + " " + probe + " path " + path
                                + " is not a Spring Boot health endpoint");
                    }
                }
            }
        }

        for (Map<String, Object> configMap : ofKind(docs, "ConfigMap")) {
            for (String key : map(configMap, "data").keySet()) {
                if (key.matches("(?i).*(PASSWORD|SECRET|TOKEN|API_KEY).*")) {
                    problems.add("ConfigMap " + name(configMap) + " holds " + key + " - secrets belong in a Secret");
                }
            }
        }
        return problems;
    }

    private static List<Map<String, Object>> load(Path dir) throws IOException {
        List<Map<String, Object>> docs = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    new Yaml().loadAll(reader).forEach(doc -> docs.add((Map<String, Object>) doc));
                }
            }
        }
        return docs;
    }

    private static List<Map<String, Object>> ofKind(List<Map<String, Object>> docs, String kind) {
        return docs.stream().filter(d -> kind.equals(d.get("kind"))).toList();
    }

    private static Map<String, Object> podLabels(Map<String, Object> deployment) {
        return map(map(map(map(deployment, "spec"), "template"), "metadata"), "labels");
    }

    private static List<Map<String, Object>> containers(Map<String, Object> deployment) {
        return list(map(map(map(deployment, "spec"), "template"), "spec"), "containers");
    }

    private static List<Object> containerPorts(Map<String, Object> deployment) {
        return containers(deployment).stream()
                .flatMap(c -> list(c, "ports").stream())
                .map(p -> p.get("containerPort"))
                .toList();
    }

    private static String name(Map<String, Object> doc) { return (String) map(doc, "metadata").get("name"); }

    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    private static List<Map<String, Object>> list(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }
}
