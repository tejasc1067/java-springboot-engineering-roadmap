# config-system

A **config server** that holds configuration centrally, and a **client** service that
imports its config from it instead of hardcoding it. The server uses the `native`
profile (config files on the classpath under `config-server/.../resources/config/`),
so no git repo is needed to run the demo.

## Automated test

```bash
mvn -q test
```
- `config-server` boots and is asked (over HTTP, as a client would) for
  `inventory-service`'s config — asserts it serves the centrally-defined values.
- `config-client` boots with an `optional:` import, so it passes even without the
  server running.

## Run it end to end

```bash
# terminal 1 — the config server (browse http://localhost:8888/inventory-service/default)
cd config-server && mvn -q spring-boot:run

# terminal 2 — the client, which fetches its config from the server on startup
cd config-client && mvn -q spring-boot:run

curl localhost:8081/api/greeting
# -> config served centrally by Spring Cloud Config | lowStockThreshold=3
#    (neither value is in the client's own application.yml — it came from the server)
```

### Refresh without a restart

1. Edit `config-server/src/main/resources/config/inventory-service.yml` (change the greeting).
   In production the server reads a git repo; here it reads the classpath, so re-run the
   server after editing (or point `search-locations` at a folder on disk to edit live).
2. Tell the client to reload: `curl -X POST localhost:8081/actuator/refresh`
3. `curl localhost:8081/api/greeting` — the new value appears, no client restart.
   `@RefreshScope` on the controller is what makes the bean pick up the change.
