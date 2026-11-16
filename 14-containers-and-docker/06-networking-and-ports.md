# Networking and Ports

A container gets its **own network**: its own IP address, its own ports, and its own `localhost`. That isolation is what lets ten containers all listen on port 8080 without fighting. It's also behind the three most common "it works on my machine but not in Docker" problems: the app can't be reached from your browser, the app is running but refuses outside connections, and one container can't find another. This topic shows each one failing, then fixed.

---

## The problem this solves

On your laptop, everything shares one network. Your Spring Boot app listens on `localhost:8080`, Postgres on `localhost:5432`, and the app finds the database at `localhost:5432`. Put each in a container and every one of those assumptions breaks:

- `localhost:8080` in your browser no longer reaches the app.
- `localhost:5432` inside the app's container is the app's container, not the database.

You need two things: a way **from the host into a container** (publishing ports), and a way **from one container to another** (networks and names).

## How it works

### From the host: publish a port with `-p`

The app inside the container listens on port 8080 *of the container*. Nothing outside can reach it until you **publish** the port:

```bash
docker run -p 14006:8080 roadmap14-06-app
#             ^host  ^container
```

`-p HOST:CONTAINER` means "forward connections on the host's port 14006 to the container's port 8080". The demo shows both sides:

```text
== From the host into a container
  no -p:                         curl: (7) Failed to connect to localhost port 14006 after 0 ms: Couldn't connect to server
PASS: without -p the app runs but the host can't reach it
  -p 14006:8080:                 hello from app
PASS: -p 14006:8080 forwards host port 14006 to container port 8080
  second container on 14006:     port is already allocated
PASS: two containers can't publish the same host port
```

Each container has its own port 8080, so ten containers can all listen on 8080 inside. But the *host* has only one port 14006, so only one container can publish it. Pick a different host port for each: `-p 14007:8080`, `-p 14008:8080`.

The `EXPOSE 8080` line in the demo's `Dockerfile` is **documentation only**. It tells readers and `docker ps` which port the app uses, and publishes nothing. Only `-p` (or `ports:` in Compose, topic 08) does.

`docker port <container>` lists what a container publishes.

### Broken: the app is listening on the container's own loopback

Spring Boot listens on all network interfaces by default (`0.0.0.0`). Someone sets `server.address=127.0.0.1`, perhaps copied from a "secure your local dev server" tip:

```text
  SERVER_ADDRESS=127.0.0.1:      inside the container: 'hello from app' | from the host: curl: (56) Recv failure: Connection reset by peer
PASS: an app bound to 127.0.0.1 answers inside its container but not through the published port
```

The app is running and healthy: `docker exec` + `curl localhost:8080` inside the container gets an answer. But `127.0.0.1` inside a container means *only this container*. Docker forwards published traffic to the container's network interface, where nothing is listening. You get "connection reset" instead of the clean "couldn't connect" from the missing `-p` case, which makes it confusing to debug.

**Fix:** don't set `server.address` in containers (or set it to `0.0.0.0`). The same applies to anything you containerise: a Node, Python, or database server must listen on `0.0.0.0` inside the container.

### From one container to another: `localhost` is not your laptop

Two services: `orders` calls `inventory`. On your laptop, `orders` has `OTHER_URL=http://localhost:14062`, and it works because `inventory` is published on 14062. Put `orders` in a container with the same setting:

```text
  orders -> localhost:14062:     FAILED calling http://localhost:14062: ResourceAccessException <- ConnectException <- ClosedChannelException
PASS: inside a container, localhost is that container - not your laptop
```

Inside the `orders` container, `localhost` is `orders` itself, and nothing is listening on its port 14062. This is the same bug as an app trying to reach its database at `localhost:5432`.

### Broken: container names on the default network

So use the other container's name instead. Every container started without `--network` joins Docker's **default bridge network**, and on that network names don't resolve:

```text
  default network, by name:      FAILED calling http://roadmap14-06-inventory:8080: ResourceAccessException <- ConnectException <- UnresolvedAddressException
PASS: on the default bridge network, container names don't resolve
```

`UnresolvedAddressException` means the name lookup failed: there's no DNS entry for `roadmap14-06-inventory`.

### Fixed: a user-defined network

Create a network and attach both containers to it. Docker runs a small DNS server on every **user-defined** network, so each container can find the others by name:

```bash
docker network create roadmap14-06-net
docker run -d --name roadmap14-06-inventory --network roadmap14-06-net -e APP_NAME=inventory roadmap14-06-app
docker run -d --name roadmap14-06-orders --network roadmap14-06-net -p 14063:8080 \
    -e APP_NAME=orders -e OTHER_URL=http://roadmap14-06-inventory:8080 roadmap14-06-app
```

```text
  user network, by name:         hello from inventory
PASS: on a user-defined network, orders reaches inventory by container name
PASS: inventory published no ports and was still reachable from orders
```

Two details in that `OTHER_URL`:

- **The name is the container's name** (`--name`). Docker Compose (topic 08) creates a network for you and uses each service's name, so `http://inventory:8080` just works there.
- **The port is the container's port (8080), not a published one.** Containers on the same network talk to each other directly. `inventory` publishes no ports at all, and doesn't need to. `-p` is only for traffic coming from the host. Publishing only what the outside world needs is also safer: here, only `orders` is reachable from the host.

To reach something running on your laptop *from* a container (say, a database you started outside Docker), Docker Desktop provides the special name `host.docker.internal`.

---

## When to use what

| You want to reach... | From... | Use |
|---|---|---|
| A container | Your browser / host | `-p HOST:CONTAINER` |
| Another container | A container | A user-defined network + `http://<container-name>:<container-port>` |
| Something on your laptop | A container | `host.docker.internal` (Docker Desktop) |
| Nothing from outside (a database only your app uses) | — | No `-p`. Put it on the app's network only. |

---

## Common pitfalls

- **Forgetting `-p`.** The app runs, the logs look fine, the browser says "can't connect". Add `-p host:container`.
- **`localhost` in configuration that runs inside a container.** `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shop` points at the app's own container. Use the database container's name on a shared network.
- **Using the published (host) port between containers.** `http://inventory:14062` fails. Containers talk on container ports: `http://inventory:8080`.
- **Relying on the default bridge network.** Names don't resolve there. Create a network, or use Compose, which creates one.
- **Binding to `127.0.0.1` inside the container.** Published traffic is reset. Listen on `0.0.0.0`.

---

## Code examples

`CODE_EXAMPLES/06-networking-and-ports/networking-demo/`. Run with `./demo.sh`, which needs Docker running, Maven, and JDK 21. Read in this order:

1. `src/main/java/com/example/network/NetworkApplication.java` — `/hello`, and `/call-other`, which calls `OTHER_URL` and reports the exception chain on failure.
2. `Dockerfile` — the image, with `EXPOSE` as documentation.
3. `demo.sh` — seven situations: no `-p`, `-p`, a port clash, a loopback-bound app, `localhost` between containers, names on the default network, names on a user-defined network.

---

## Try this yourself

1. Start one container with `-p 14006:8080` and another with `-p 14007:8080`. Both apps listen on 8080 inside. Why is that fine?
2. Run `docker network inspect roadmap14-06-net` while the demo's last step is running (add a `sleep 60` before `exit` in `demo.sh`). Find each container's IP address. Why should your configuration use names instead of these IPs?
3. Start `inventory` on the network with `-e SERVER_PORT=9090`. What must change in `orders`' `OTHER_URL`, and does `-p` on `inventory` matter?

## Self-check

1. Explain what `-p 14006:8080` does, which side is which, and why two containers can both listen on 8080 but can't both publish 14006.
2. Inside the `orders` container, why does `http://localhost:14062` fail even though the same URL works from your laptop's browser?
3. Two containers on the default network can't reach each other by name, but they can on a network you created. What's different, and which port goes in the URL?
