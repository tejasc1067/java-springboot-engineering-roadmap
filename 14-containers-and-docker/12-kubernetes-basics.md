# Kubernetes Basics

Docker runs containers on one machine. Docker Compose (topic 08) runs a few related containers on one machine. **Kubernetes** (often written **k8s**) runs containers across *many* machines, and keeps them running. You describe what you want ("two copies of the orders app, reachable at the name `orders`, restarted if they hang"), and Kubernetes works continuously to make reality match that description.

This topic is an introduction for a backend developer: the five objects you'll write for every Spring Boot service, how they connect, and the mistakes that leave a perfectly valid setup receiving no traffic. Running and operating clusters is its own discipline, and it stays out of this roadmap.

---

## The problem this solves

You have a container image (topics 03 and 10). In production you need more than `docker run`:

- **More than one copy**, so one crash or one slow machine doesn't take the service down.
- **Automatic replacement** when a copy crashes, hangs, or its machine dies.
- **A stable address.** Copies come and go with new IP addresses, but callers need one name to call.
- **Rolling updates**: deploy version 1.1 without downtime, and go back if it misbehaves.
- **Configuration and secrets** supplied from outside the image (topic 05).

Kubernetes does all of this from YAML files you write and keep in Git next to your code.

---

## How it works

### The vocabulary, from the bottom up

- **Cluster**: a set of machines (**nodes**) run by Kubernetes. You talk to it through its API, using the `kubectl` command.
- **Pod**: the smallest thing Kubernetes runs. Usually one container (your app) plus its own IP address. Pods are disposable: when one dies it isn't repaired, it's replaced with a new pod and a new IP.
- **Deployment**: "keep N pods of this image running". It creates the pods, replaces dead ones, and rolls out new versions gradually. You almost never create pods directly; you create a Deployment.
- **Service**: a stable name and virtual IP in front of a changing set of pods. Callers use `http://orders`, and the Service forwards each request to one of the pods that is currently **ready**. This is server-side load balancing; module 13 topic 13 showed the client-side kind.
- **ConfigMap** and **Secret**: key-value settings stored in the cluster and injected into pods, usually as environment variables. Spring Boot reads them like any other environment variable (module 08 topic 05).

How they find each other is the part that matters most, and it's done with **labels**: small `key: value` tags. The Deployment stamps a label on every pod it creates (`app: orders`), and the Service sends traffic to pods with a matching label. There are no direct references. If the labels don't match, nothing connects, and nothing reports an error.

```text
                 Service "orders"  (selector: app=orders, port 80 -> targetPort 8080)
                          |
          +---------------+----------------+
          v                                v
   Pod  (labels: app=orders)        Pod  (labels: app=orders)       <- created and replaced by
   container: orders:1.0.0          container: orders:1.0.0            Deployment "orders" (replicas: 2)
   listens on 8080                  listens on 8080
   env from ConfigMap + Secret      env from ConfigMap + Secret
```

### The Deployment

From `k8s-manifests-demo/fixed/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders
spec:
  replicas: 2
  selector:
    matchLabels:
      app: orders              # which pods this Deployment manages...
  template:                    # ...and the blueprint for each pod it creates
    metadata:
      labels:
        app: orders
    spec:
      containers:
        - name: orders
          image: roadmap14/orders:1.0.0      # a pinned tag, never :latest (topic 10)
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: orders-config
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: orders-db
                  key: password
          resources:
            requests:            # what the scheduler reserves on a node for this pod
              cpu: "250m"        # a quarter of a CPU core
              memory: "512Mi"
            limits:
              memory: "768Mi"    # above this the container is killed ("OOMKilled") and restarted
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
```

**Resources.** `requests` is what Kubernetes reserves on a node when it decides where to place the pod. `limits.memory` is a hard ceiling: if the JVM's total memory goes over it, the container is killed. The JVM sizes its default heap from the container's memory limit (a quarter of it by default), so set the limit deliberately and leave room for non-heap memory (module 03d).

**Probes** are how Kubernetes asks your app how it's doing. Topic 09 built the two Spring Boot endpoints, and Spring Boot turns them on automatically when it detects it's running in Kubernetes. The two probes do different things when they fail:

| Probe | Question | When it fails |
|---|---|---|
| **readiness** | "Can you take traffic right now?" | The pod is taken **out of the Service** until it passes again. Nothing is restarted. |
| **liveness** | "Are you stuck beyond repair?" | The container is **restarted**. |

Use readiness for "warming up" or "my database is unreachable right now", and liveness only for "this JVM is wedged". A liveness probe that checks the database restarts every pod whenever the database blips, which turns a database problem into a full outage. [Module 17 topic 08](../17-observability/08-liveness-and-readiness.md) simulates these probes against a Spring Boot app: the restart storm, startup probes, and the health-group settings that silently break readiness.

### The Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: orders
spec:
  selector:
    app: orders                # send traffic to READY pods with this label
  ports:
    - port: 80                 # what callers use: http://orders
      targetPort: 8080         # where the app listens inside the pod
```

Other pods in the cluster now call `http://orders`. Kubernetes' internal DNS resolves the name, just like service names in a Compose network (topic 08). The Service tracks which pods match and are ready, and that list is visible as its **endpoints**.

### ConfigMap and Secret

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: orders-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://orders-db:5432/orders"
  SPRING_DATASOURCE_USERNAME: "orders"
```

The password is not in this file. It's in a **Secret**, created by a command or a secrets tool rather than committed to Git:

```bash
kubectl create secret generic orders-db --from-literal=password='...'
```

The Deployment reads it with `secretKeyRef`, as an environment variable. A Secret can also be mounted as files in a directory, which Spring Boot reads with `spring.config.import=configtree:...` (topic 05). Files don't show up in a process listing or in the output of `env`. Be clear about what a Secret is: its values are only **base64-encoded**, not encrypted, so anyone who can read Secrets in the cluster can read the password. What a Secret gives you is a separate object with its own access rules, kept out of your Git history and out of the image (topic 05). Real clusters add encryption at rest or an external secret store.

---

## Broken: four mistakes Kubernetes accepts without complaint

Every file in `broken/` is valid YAML and a valid Kubernetes object, and the API server accepts them. The result is still an app that gets no traffic, or leaks a password:

```yaml
# broken/service.yaml
spec:
  selector:
    app: order-service         # no pod has this label
  ports:
    - port: 80
      targetPort: 8081         # the app listens on 8080
```

```yaml
# broken/deployment.yaml
readinessProbe:
  httpGet:
    path: /health/readiness    # Spring Boot serves /actuator/health/readiness
```

```yaml
# broken/configmap.yaml
SPRING_DATASOURCE_PASSWORD: "s3cr3t-prod-password"   # a password in a ConfigMap, committed to Git
```

What each does in a running cluster:

1. **Selector doesn't match the pod labels.** The Service has zero endpoints. Calls to `http://orders` fail, while `kubectl get pods` shows both pods `Running`.
2. **`targetPort` doesn't match `containerPort`.** Traffic is sent to a port nothing listens on, so connections are refused.
3. **Wrong readiness path.** The probe gets a 404, the pod never becomes ready, and the Service never sends it traffic. A rollout of a new version stalls waiting for pods that will never be ready.
4. **Password in a ConfigMap.** It works. It's also in plain text in Git, readable by anyone who can read ConfigMaps.

`kubectl apply --dry-run=client` won't catch any of these. It checks that the objects are well formed, not that they make sense together. It also still needs a reachable cluster: on a machine without one it fails with `failed to download openapi ... connection refused`, even in dry-run mode.

So the demo checks the manifests the way a reviewer would, with a test. `ManifestChecks` parses every YAML file with SnakeYAML and checks how they fit together. `ManifestTest` runs it over both folders:

```text
Service orders selects {app=order-service} but no pod has those labels
Service orders sends to targetPort 8081 but the container listens on [8080]
Deployment orders readinessProbe path /health/readiness is not a Spring Boot health endpoint
ConfigMap orders-config holds SPRING_DATASOURCE_PASSWORD - secrets belong in a Secret
```

`brokenManifests_haveFourBugsKubectlWouldAccept` asserts exactly these four, and `fixedManifests_passEveryCheck` asserts `fixed/` has none. Real teams use tools for the same job (`kubeconform` for schemas; `kube-linter` or policy engines for rules like "no secrets in ConfigMaps"), run in CI. The demo's checker is about 120 lines, so you can read every rule.

### Trying it on a real cluster

You don't need a cluster for this topic. To see it run, Docker Desktop includes a single-node Kubernetes (Settings → Kubernetes → Enable Kubernetes), or use `kind` or `minikube`. Build the image (topic 03), make it available to the cluster, then:

```bash
kubectl create secret generic orders-db --from-literal=password='change-me'
kubectl apply -f fixed/
kubectl get pods                   # two pods; READY 1/1 once the readiness probe passes
kubectl get endpoints orders       # two pod IPs behind the Service
kubectl apply -f broken/service.yaml
kubectl get endpoints orders       # <none> - mistake 1, visible
```

---

## When to use it / when not to

| Use Kubernetes when | Don't, when |
|---|---|
| Your organisation already runs it. Then this topic is what you need to ship a service onto it. | You run one or two services. A managed platform, or Compose on a VM, is far less to learn and operate. |
| You run many services that need rolling deploys, self-healing, and scaling | You're learning backend development. Get one service right first; Kubernetes adds nothing to the app itself. |

As an application developer you'll mostly write and review these five objects. Clusters, networking add-ons, ingress controllers, autoscaling, and Helm charts are platform-team territory and outside this roadmap.

---

## Common pitfalls

- **Service selector ≠ pod labels.** Zero endpoints, and no error. Copy the label from `template.metadata.labels`, and check with `kubectl get endpoints`.
- **`targetPort` ≠ `containerPort`.** Connection refused. Keep them in sync, or name the port (`name: http`) and use `targetPort: http`.
- **A liveness probe that checks dependencies.** A database blip restarts every pod. Liveness checks only the JVM itself; dependencies belong in readiness (topic 09).
- **No memory limit, or a limit with no headroom.** Without a limit, one leaking pod can starve its node. With a tight one, the JVM is OOMKilled under load. Set it deliberately and watch real usage (module 17).

---

## Code examples

`CODE_EXAMPLES/12-kubernetes-basics/k8s-manifests-demo/`. Run `mvn -q test`; no cluster or Docker needed. Read in this order:

1. `fixed/deployment.yaml` — 2 replicas, labels, resources, probes, config from a ConfigMap and the password from a Secret.
2. `fixed/service.yaml` — selector and `port` → `targetPort`.
3. `fixed/configmap.yaml` — non-secret settings as Spring Boot environment variables.
4. `broken/` — the same three files with the four mistakes (search for `BUG`).
5. `src/test/java/com/example/k8s/ManifestChecks.java` — how the manifests are checked against each other.
6. `src/test/java/com/example/k8s/ManifestTest.java` — the four problems in `broken/`, none in `fixed/`.

---

## Try this yourself

1. Add a second label `tier: backend` to the pod template in `fixed/deployment.yaml`, but not to the Service's selector. Does the Service still match? Now do it the other way round. Run the test each time.
2. Rename the container port: `- name: http` with `containerPort: 8080`, and set the Service's `targetPort: http`. Extend `ManifestChecks` so a named `targetPort` is matched against port names.
3. Add a check to `ManifestChecks` that fails when a container image uses the `:latest` tag or no tag at all, with a broken example to prove it.

## Self-check

1. Explain pod, Deployment, and Service in one sentence each, and how a Service decides which pods get traffic.
2. Your pods show `Running`, but every call to `http://orders` fails. Name two manifest mistakes that cause exactly this, and the `kubectl` command that shows the problem.
3. What is the difference between a failing readiness probe and a failing liveness probe? Why is checking the database in a liveness probe dangerous?
4. Why should a database password go in a Secret rather than a ConfigMap, and what does a Secret *not* protect you from?
