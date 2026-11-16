# k8s-manifests-demo

Kubernetes manifests for a Spring Boot "orders" app, in two versions:

- `fixed/` — ConfigMap, Deployment (2 replicas, resources, liveness + readiness probes, password from a Secret), Service.
- `broken/` — the same, with four mistakes the Kubernetes API accepts without complaint.

`ManifestTest` parses the YAML and checks what a reviewer would: does the Service select real pods, does its
`targetPort` match a `containerPort`, are the probes on Spring Boot's health endpoints, is anything secret
sitting in a ConfigMap.

Run (no cluster needed):

```
mvn -q test
```

It prints the four problems found in `broken/` and asserts `fixed/` has none.

With a cluster (Docker Desktop → Settings → Kubernetes → Enable, or `kind`/`minikube`):

```
kubectl create secret generic orders-db --from-literal=password='change-me'
kubectl apply -f fixed/
kubectl get pods,svc,endpoints
```

`kubectl apply --dry-run=client` still needs a reachable cluster - it asks the API server which kinds
exist - so this demo checks the manifests with a test instead.
