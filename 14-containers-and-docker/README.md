# 14 — Containers and Docker

By module 08 you could build a Spring Boot app into one executable jar, and by module 13 you had several of them talking to each other. This module is about **shipping** them: packaging an app, its exact Java runtime, and its operating-system libraries into an **image**, and running that image as a **container** that behaves the same on your laptop, a teammate's laptop, a CI machine, and a production server.

Containers are how almost every backend service is deployed today. You'll write a production-quality Dockerfile for a Spring Boot app, wire it to a real Postgres with Docker Compose, test against real infrastructure with Testcontainers, and read the Kubernetes manifests that run it at scale. Each topic shows the mistake first, a real container doing the wrong thing, and then the fix.

## Who this is for

- Anyone who has finished modules 08–13 and has only ever started apps with `mvn spring-boot:run` or `java -jar`.
- Developers who copy-paste Dockerfiles from blog posts and want to know what each line does and what it costs.
- Anyone whose app "works on my machine" and nowhere else.

## What you'll be able to do at the end

- [ ] Explain what an image, a container, and a layer are, and how a container differs from a virtual machine.
- [ ] Write a multi-stage Dockerfile for a Spring Boot app that produces a small runtime image without source code or build tools in it.
- [ ] Structure an image so a one-line code change rebuilds only a small layer, and build an image with buildpacks (`spring-boot:build-image`).
- [ ] Pass configuration and secrets into a container at runtime instead of baking them into the image, and prove the image contains none.
- [ ] Publish ports, bind the app to the right address, and let containers find each other by name on a network.
- [ ] Keep a database's data across container restarts and removals with a volume.
- [ ] Run an app and Postgres together with Docker Compose, with the app waiting until the database is healthy.
- [ ] Add a health check, and shut a container down without dropping in-flight requests.
- [ ] Run as a non-root user, shrink the image, and explain what distroless images remove.
- [ ] Replace H2 with a real Postgres in tests using Testcontainers, and show a bug H2 hides.
- [ ] Read and write a Kubernetes Deployment, Service, ConfigMap, and probes for a Spring Boot app.

If you can do all eleven, the module worked.

## Prerequisites

- **Modules 08 and 10 done.** You need a Spring Boot app you can package as a jar (module 08 topic 08), Actuator health endpoints (08-12), and Spring Data JPA (module 10) for the database topics.
- **Docker installed and running.** See [`SETUP.md`](../SETUP.md), "Installing Docker (module 14 onwards)", for Windows, macOS, and Linux, plus a verification command. Every demo in this module needs it.
- **Disk space and a network connection.** The first run of each demo downloads base images. On disk, `eclipse-temurin:21-jre` takes about 480 MB, `maven:3.9-eclipse-temurin-21` about 850 MB, and `postgres:17` about 700 MB; budget roughly 5 GB for the whole module. After the first download they're cached.
- **A terminal.** Most demos here are `./demo.sh` scripts that run real `docker` commands and print `PASS`/`FAIL` for each check. Read the script: the commands in it are the lesson. On Windows, run them from WSL2 or Git Bash.

## How this module is organized

Topics 01–02 are **the model**: why containers exist (01) and what images, containers, and layers actually are (02). Everything later builds on these two.

Topics 03–05 are **building the image**: a good Dockerfile for Spring Boot (03), making rebuilds fast with layers and buildpacks (04), and keeping configuration and secrets *out* of the image (05).

Topics 06–09 are **running containers**: ports and networks (06), data that survives (07), several containers together with Compose (08), and health checks plus graceful shutdown (09).

Topic 10 is **hardening**: not running as root, and making the image smaller and harder to attack.

Topic 11 is **testing with real infrastructure**: Testcontainers starts a real Postgres for your tests and catches what H2 hides.

Topic 12 is **the next step**: a first look at Kubernetes, which runs and restarts containers across many machines. It's an introduction, not a Kubernetes course.

```text
01-why-containers                     <- works-on-my-machine; containers vs virtual machines
02-images-containers-and-layers       <- image vs container, layers, the writable layer, docker history
03-dockerfile-for-spring-boot         <- naive single-stage image vs multi-stage; .dockerignore
04-layered-jars-and-buildpacks        <- cache-friendly layers; spring-boot:build-image
05-configuration-and-secrets          <- env vars and profiles at runtime; secrets baked into an image (broken) vs injected
06-networking-and-ports               <- -p, binding to 0.0.0.0, container-to-container by name
07-volumes-and-data                   <- data lost with the container vs named volumes and bind mounts
08-docker-compose                     <- app + Postgres; service names; healthcheck + depends_on
09-healthchecks-and-graceful-shutdown <- HEALTHCHECK; SIGTERM, PID 1, and in-flight requests
10-image-hardening                    <- root by default; non-root user; distroless; image size
11-testcontainers                     <- H2 passes, Postgres fails; real database in tests
12-kubernetes-basics                  <- pod, deployment, service, configmap, probes
```

```text
COMMON_MISTAKES.md       <- real container bugs with commands: secrets in layers, localhost inside a container, PID 1 and SIGTERM, root user, H2 vs Postgres, probes
INTERVIEW_QNA.md         <- interview-grade questions: container vs VM, layers and caching, config and secrets, graceful shutdown, Testcontainers vs H2, Kubernetes basics
```

## The toolkit

| Tool / image | What it does | First used in |
|---|---|---|
| Docker Engine + CLI (Docker Desktop on Mac/Windows) | builds images, runs containers | every topic |
| `eclipse-temurin:21-jre` / `21-jdk` | official OpenJDK runtime and JDK base images | topic 01 |
| `maven:3.9-eclipse-temurin-21` | Maven + JDK, for the build stage of a multi-stage Dockerfile | topic 03 |
| Spring Boot Maven plugin (`spring-boot:build-image`) | builds an image with Cloud Native Buildpacks, no Dockerfile | topic 04 |
| `postgres:17` | the database in the volume, Compose, and Testcontainers topics | topic 07 |
| Docker Compose (`docker compose`, built into Docker Desktop) | runs several containers from one `compose.yaml` | topic 08 |
| `gcr.io/distroless/java21-debian12:nonroot` | a minimal Java runtime image without a shell or package manager | topic 10 |
| Testcontainers (`org.testcontainers:postgresql`, Boot-managed version) + `@ServiceConnection` | starts real containers from JUnit tests | topic 11 |
| `kubectl` (ships with Docker Desktop) | the Kubernetes command-line client, for applying the manifests to a local cluster (optional) | topic 12 |

The Spring Boot demos stay on Spring Boot 3.5.14 and Java 21, like modules 08–13.

## How to run the examples

Each topic has its own demo under `CODE_EXAMPLES/NN-topic-name/`. Most are run with one script:

```bash
cd 14-containers-and-docker/CODE_EXAMPLES/03-dockerfile-for-spring-boot/<demo-dir>
./demo.sh
```

The script builds what it needs, runs the broken version and the fixed version, prints a `PASS`/`FAIL` line for each check, and removes every container, network, and volume it created when it exits. Images are kept, so the second run is fast. All of them start with `roadmap14-`, so `docker images | grep roadmap14` lists them and `docker rmi $(docker images -q 'roadmap14-*')` removes them.

Topics 11 and 12 are normal Maven test projects: `mvn -q test`. Topic 11's tests start a real Postgres container themselves. Topic 12's tests check Kubernetes manifests without a cluster (`kubectl apply --dry-run=client` still needs one to talk to); its README says how to try them on a local cluster if you want to.

## Checkpoint

You're done with this module when you can:

1. Explain to a teammate why their app works on their laptop but fails in a container on the server, and how an image prevents it.
2. Take a single-stage Dockerfile that copies the whole project into a Maven image and turn it into a multi-stage build on a JRE image. Say how big each image is and what's in the bigger one that shouldn't be.
3. Find a database password in an image using only `docker history` or `docker inspect`, and rewrite the setup so the image contains no secret.
4. Explain why `curl localhost:8080` from inside one container can't reach another container, and what to use instead.
5. Write a `compose.yaml` for an app and Postgres where the app waits for the database to be healthy and the data survives `docker compose down`.
6. Explain why `docker stop` sometimes waits the whole stop timeout and then kills the app with exit code 137, and fix the Dockerfile.
7. Show a query that passes against H2 and fails against Postgres, and the Testcontainers setup that catches it.
8. Read a Kubernetes Deployment and Service and point out why the Service has no endpoints.

If those eight feel comfortable, move to module 15, where the services you can now ship get a cache in front of their database.

## A note on what's *not* here

- **No cloud.** No ECR/GCR/ACR registries, ECS, EKS, GKE, or AKS. They're vendor-specific and belong in a cloud roadmap. Everything here runs on your machine.
- **No CI/CD pipelines.** Building and pushing images from CI is outside this roadmap's scope (see the root README).
- **No deep Kubernetes.** Topic 12 is the vocabulary and the manifests a backend engineer reads every week. Helm, operators, ingress controllers, autoscaling, and cluster administration are a separate skill.
- **No service mesh.** Istio and Linkerd are out of scope.
- **No Docker internals.** Namespaces and cgroups get one paragraph in topic 01, which is all you need to use containers well.
