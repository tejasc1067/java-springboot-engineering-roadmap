# 08 — Java Networking Basics

A backend service is a program reachable over a network. To understand what your service does — long before you reach Spring or Tomcat — you need to know how Java talks to a network: sockets, TCP vs UDP, blocking I/O, and what "thread per connection" actually means.

This topic covers the `java.net` package: opening a TCP connection, accepting connections on a `ServerSocket`, sending and receiving bytes, and the limits of the classic blocking model. Topic 09 covers Java NIO (non-blocking I/O), which is what real servers like Netty use.

---

## The vocabulary

| Term | What it means |
|------|------------|
| **IP address** | A network-level identifier for a host. IPv4 (`93.184.216.34`) or IPv6 (`2606:2800:220:1::248:1893`). |
| **Port** | A number 0-65535 that distinguishes services on the same host. |
| **Hostname** | A human-friendly name; DNS translates it to an IP. |
| **Socket** | An endpoint for two-way byte communication. |
| **TCP** | Connection-oriented, reliable, ordered, slower-but-correct. |
| **UDP** | Connectionless, unreliable, unordered, faster-but-best-effort. |

Well-known ports:

| Port | Service |
|------|---------|
| 22 | SSH |
| 80 | HTTP |
| 443 | HTTPS |
| 3306 | MySQL |
| 5432 | PostgreSQL |
| 6379 | Redis |

Ports under 1024 are "privileged" — Linux requires root to bind to them. That's why services often listen on 8080/8443 in dev/containers.

---

## TCP vs UDP

**TCP** is what almost everything uses: HTTP, SSH, databases, most RPC.

- **Reliable**: lost packets are retransmitted.
- **Ordered**: receiver sees bytes in the order sent.
- **Connection-oriented**: 3-way handshake to set up, 4-way to tear down.
- **Flow- and congestion-controlled**: the protocol slows down when the network is busy.

**UDP** is for cases where TCP's overhead is too much:

- **DNS** (1 packet per query, no setup cost worth it).
- **Streaming media** (one lost packet is better than a stall).
- **Game state** (next update obsoletes the last anyway).
- **Telemetry**.

In Java, `Socket` + `ServerSocket` mean TCP. `DatagramSocket` means UDP.

---

## A TCP client

```java
try (Socket socket = new Socket("example.com", 80)) {
    socket.setSoTimeout(5000);                         // give up after 5s of no data
    OutputStream out = socket.getOutputStream();
    out.write("GET / HTTP/1.0\r\nHost: example.com\r\n\r\n".getBytes());
    out.flush();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
        String line;
        while ((line = r.readLine()) != null) System.out.println(line);
    }
}
```

What happens:

1. `new Socket(host, port)` — DNS lookup, then TCP handshake.
2. `getOutputStream().write(...)` — bytes head out the network card.
3. `getInputStream().read(...)` — block until bytes arrive or the connection closes.
4. `try-with-resources` closes the socket on exit, which sends a FIN.

`setSoTimeout(ms)` is critical. Without it, a slow or dead peer hangs your thread forever.

---

## A TCP server

```java
try (ServerSocket server = new ServerSocket(9000)) {
    System.out.println("listening on 9000");
    while (!Thread.currentThread().isInterrupted()) {
        Socket client = server.accept();             // BLOCKS until a client connects
        handle(client);
    }
}
```

`ServerSocket.accept()` blocks one thread until someone connects. Inside `handle(client)` you read and write through `client.getInputStream()` / `client.getOutputStream()`.

The model is **synchronous**: one connection at a time on this thread. For more concurrency, hand each accepted socket to a thread pool.

```java
ExecutorService pool = Executors.newFixedThreadPool(50);
while (!Thread.currentThread().isInterrupted()) {
    Socket client = server.accept();
    pool.submit(() -> handle(client));
}
```

This is the classic **thread-per-connection** model. It scales to a few thousand connections before the OS thread count or stack memory becomes a problem. Beyond that, NIO (topic 09) is the answer.

---

## Blocking I/O — what "blocked" means

A `read()` on a `Socket` blocks the *calling thread* until:

- Data arrives (returns the bytes read).
- The peer closes the connection (returns `-1`).
- A timeout fires (`SocketTimeoutException`).
- The socket itself is closed by another thread (causes `SocketException`).

While blocked, the thread does nothing. The OS scheduler skips over it; it consumes a stack (~1MB) but no CPU. That's the basic resource cost of every parked thread in a thread-per-connection server: one stack per parked connection.

For 100 connections, that's 100MB of stacks. For 10,000, it's 10GB — usually unaffordable. This is the scaling wall NIO addresses.

---

## Reading until "done"

A common bug: assuming one `read` gives you the whole message.

```java
byte[] buf = new byte[8192];
int n = in.read(buf);                  // returns SOME bytes, possibly fewer than requested
```

TCP is a **stream of bytes**, not a sequence of messages. A peer might send 1000 bytes; you might receive them split into 200 + 800 across two `read` calls. Or you might get part of message 1 and the start of message 2 in one `read`.

The fix: define a **protocol** that says where one message ends and the next begins:

- **Length-prefixed**: send a 4-byte length, then the body.
- **Delimited**: send body, then a sentinel byte (`\n`, `\0`).
- **Fixed-size**: every message is exactly N bytes.

HTTP uses both: `Content-Length` header (length-prefix) and `\r\n` line delimiters.

---

## UDP, briefly

```java
try (DatagramSocket socket = new DatagramSocket()) {
    byte[] data = "ping".getBytes();
    InetAddress addr = InetAddress.getByName("localhost");
    socket.send(new DatagramPacket(data, data.length, addr, 9999));

    byte[] recvBuf = new byte[1024];
    DatagramPacket reply = new DatagramPacket(recvBuf, recvBuf.length);
    socket.receive(reply);
    System.out.println(new String(reply.getData(), 0, reply.getLength()));
}
```

Each `send` is one packet. If it's lost, you don't know. If the receiver is too slow, packets are dropped silently. Application-level retransmission and deduplication is your job.

UDP packets in IPv4 are limited to ~65KB; in practice, you want them under ~1400 bytes to fit in a single Ethernet frame.

---

## Common pitfalls

- **No timeout.** A connection to a dead peer hangs the thread forever. Always `setSoTimeout`.
- **Assuming `read` returns a whole message.** Define a protocol with delimiters or length prefixes.
- **Not closing sockets.** Each open socket consumes a file descriptor; OS limits are low (often 1024 by default). `try-with-resources` solves this.
- **Address-resolution surprises.** `InetAddress.getByName(...)` may block on DNS for seconds if the resolver is slow. Production code uses async DNS or caches.
- **Thread-per-connection at high scale.** 10k concurrent connections = ~10GB of stacks. Use NIO (topic 09) or a server framework that does.
- **Endianness bugs.** Java's `DataInputStream` is big-endian; many non-Java peers are little-endian. Convert explicitly.
- **`Nagle's algorithm` adding latency.** TCP buffers small writes by default. For latency-sensitive protocols, `socket.setTcpNoDelay(true)` disables it.

---

## Code examples

1. `EchoServer.java` — a single-threaded TCP server that echoes input back; close with Ctrl-C.
2. `EchoClient.java` — a TCP client that connects to `EchoServer` and prints the echo.
3. `MultiThreadedEchoServer.java` — accept loop hands each connection to a thread pool.
4. `LengthPrefixedProtocol.java` — read messages of varying size correctly with a length prefix.
5. `UdpPingPong.java` — minimal UDP client + server in one file.

All five use only `java.net` (plus `java.util.concurrent` for the pool). No external libraries.

Each `main` listens on a port. To avoid colliding with anything you have running, the examples use 19000+. If the port is busy on your machine, edit the constant near the top.

---

## Try this yourself

1. Run `EchoServer` in one terminal, `EchoClient` in another. Confirm the echo.
2. In `MultiThreadedEchoServer`, time how long the server takes to handle 20 concurrent clients (write a tiny script or just open multiple `EchoClient` instances). Then drop the pool size to 4. Watch latency rise.
3. In `LengthPrefixedProtocol`, deliberately send a message in two `write` calls. Confirm the reader still assembles it correctly.

---

## Self-check

1. Why does a TCP `read` need a timeout in production code, and what specifically happens to the thread when no data ever arrives?
2. You write `out.write("HELLO".getBytes())` and the peer calls `in.read(buf)` once. Are you guaranteed `read` returns exactly 5 bytes? Why or why not?
3. A service holds 10,000 simultaneous TCP connections with thread-per-connection. Each thread uses ~1MB of stack. What's the memory cost in MB, and what's the alternative architecture?
