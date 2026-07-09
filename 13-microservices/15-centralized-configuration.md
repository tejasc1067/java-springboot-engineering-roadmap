# Centralized Configuration

Every service has configuration: database URLs, downstream addresses, timeouts, feature flags, credentials. In a monolith that's one `application.yml`. Across a dozen services running in multiple environments (dev, staging, prod) and multiple instances each, configuration sprawls: the same setting copied into a dozen repos, changed by hand in a dozen places, drifting out of sync, and requiring a redeploy of every service to change a value. **Centralized configuration** pulls it into one managed, versioned source that services fetch at startup — and can refresh at runtime without a redeploy. This is the "there is one administrator" fallacy (topic 02) made tractable: config lives in one auditable place instead of scattered and untraceable.

The demo runs a Spring Cloud Config server that serves a client's config from a central location.

---

## The problem this solves

You need to change inventory-service's timeout from 500ms to 800ms across all three of its instances, in production only. Without centralized config:

- The value lives in each service's packaged `application.yml`, so changing it means **editing code, rebuilding, and redeploying** — a full release cycle for a one-line config change.
- Per-environment values (dev vs prod database URLs) are managed by profiles scattered across repos, easy to get wrong.
- Secrets end up committed to repos or copied into deploy scripts.
- There's no single audit trail of "who changed what config when."

Centralized config fixes this: the timeout lives in one place (ideally a git repo, so every change is versioned and reviewable), services read it at startup, and a change can be pushed to running services without rebuilding them.

## How it works: a config server services fetch from

Spring Cloud Config has two sides:

**The server** (`@EnableConfigServer`) holds the config and serves it over HTTP, keyed by `{application}/{profile}`. It reads from a backend — normally a **git repo** (versioned, auditable), or for the demo the **native** profile (files on the classpath, no git needed):

```yaml
# config-server: serve files from the classpath (native); production would use git
spring:
  profiles: { active: native }
  cloud: { config: { server: { native: { search-locations: classpath:/config/ } } } }
```

Config for a service named `inventory-service` lives in `inventory-service.yml`:

```yaml
inventory:
  greeting: "config served centrally by Spring Cloud Config"
  low-stock-threshold: 3
```

The demo's test asks the server for `GET /inventory-service/default` and asserts it returns those values — that's exactly what a client does.

**The client** imports its config from the server at startup, keyed by its own `spring.application.name`:

```yaml
# config-client (application.yml): none of the inventory.* values are here — they come from the server
spring:
  application: { name: inventory-service }
  config:
    import: "optional:configserver:http://localhost:8888"
```

The client's `${inventory.greeting}` and `${inventory.low-stock-threshold}` resolve to values it never declared locally — they were fetched from the server. Run both (README) and `curl localhost:8081/api/greeting` returns the centrally-served values.

## Refresh: changing config without a redeploy

By default a service reads config once, at startup. To change a value on a *running* service without restarting it, Spring Cloud adds `@RefreshScope` + the `/actuator/refresh` endpoint:

```java
@RestController
@RefreshScope   // beans in this scope are rebuilt when a refresh is triggered
static class GreetingController {
    GreetingController(@Value("${inventory.greeting:...}") String greeting) { ... }
}
```

Change the value on the server, then `POST /actuator/refresh` to the client — the `@RefreshScope` beans are recreated with the new value, no restart. (At scale you don't POST to each instance by hand; **Spring Cloud Bus** broadcasts a refresh to all instances over a message broker. That's the same messaging idea from topic 07, applied to config.)

## Precedence, environments, and secrets

- **Per-environment config** comes from the `{profile}` in `{application}/{profile}`: `inventory-service.yml` is the base, `inventory-service-prod.yml` overrides it in prod. Same service, different values per environment, all in the central source.
- **Precedence:** imported remote config generally overrides local defaults, and more specific (profile-specific) files override base files. Keep a sane local default (the `${...:default}` fallback) so a service is debuggable even if the server is unreachable — the demo uses `optional:` so the client still boots with defaults.
- **Secrets:** a config server *can* serve secrets, and Spring Cloud Config supports encryption at rest, but dedicated secret managers (Vault, cloud secret managers) are the better home for credentials. The roadmap flags secrets management as out of scope (module 12's note) — the point here is *don't hardcode or commit secrets*; centralize and, ideally, delegate them to a secrets store.

## When to use it / when not to

- **Use centralized config** when you have several services and/or multiple environments and want config changes without redeploys, versioned and auditable in one place.
- **A single small service** doesn't need a config server — its `application.yml` + environment variables are enough. The server earns its keep with many services/environments.
- **On some platforms you won't run your own.** Kubernetes ConfigMaps/Secrets, or a cloud config/secrets service, cover much of this; don't stack a second config system on one your platform already provides. Spring Cloud Config is the app-level option and the one that makes the mechanism explicit.
- **Keep a local fallback.** Even with central config, services should have sane defaults so they don't become undeployable when the config server is down (don't make the config server a hard startup dependency for everything unless you've made it highly available).

## Common pitfalls

- **Config duplicated across services/repos.** The problem this solves — one value, one place. Copies drift.
- **Config baked into the deployable.** If changing a timeout requires a rebuild, config isn't externalized. It should be fetchable/overridable at deploy or runtime.
- **The config server as an unguarded single point of failure.** If every service *hard*-fails to start without it, an outage of the config server is an outage of everything. Make it highly available and keep local fallbacks (`optional:`, defaults).
- **Secrets in plain config / committed to git.** Encrypt at rest or use a secrets manager; never commit credentials.
- **Forgetting refresh needs `@RefreshScope`.** Changing server config does nothing to a running client until it refreshes, and only `@RefreshScope` (or `@ConfigurationProperties`) beans pick up the change. Non-refresh-scoped singletons keep the old value until restart.

---

## Code examples

Runnable demo — `15-centralized-configuration/config-system/`:

**Automated (`mvn test` at `config-system/`):**
1. `config-server/src/main/java/com/example/configserver/ConfigServerApplication.java` + `.../resources/config/inventory-service.yml` — the server and the config it serves.
2. `config-server/src/test/java/com/example/configserver/ConfigServerTest.java` — asks the server for `inventory-service`'s config over HTTP and asserts the centralized values.

**Bootable (see folder README):**
3. `config-client/.../ConfigClientApplication.java` + `application.yml` — a service that imports its config from the server and exposes it, with `@RefreshScope` for runtime reload.

## Try this yourself

1. Add `config-server/.../resources/config/inventory-service-prod.yml` with a different `inventory.greeting`. Run the client with `--spring.profiles.active=prod` and confirm it gets the prod value — same service, environment-specific config from the central source.
2. Run both apps, `curl /api/greeting`, then change the server's `inventory-service.yml`, restart the server, `POST /actuator/refresh` to the client, and `curl` again. The value changes with no client restart — that's `@RefreshScope` at work.
3. Point the client's import at a config server that isn't running, once with `optional:` and once without. Observe: `optional:` boots with defaults; without it, the client refuses to start. Decide which you'd want for a critical vs. non-critical setting.

## Self-check

1. What's wrong with each service keeping its config in its own packaged `application.yml`, across a dozen services and three environments?
2. How does a client get its configuration from the server — what key does the server use to find the right config, and where does per-environment variation come from?
3. A value changes on the config server. What must be true for a *running* client to pick it up without a restart, and how would you push that change to 20 instances at once?
