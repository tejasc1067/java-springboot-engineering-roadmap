# 09 — Java NIO

`java.io` is "stream of bytes, one thread per stream, blocking calls." That works fine up to a few thousand connections. Beyond that, you run out of stacks. **NIO** (New I/O, introduced in Java 1.4 and extended in NIO.2 in Java 7) is the alternative: **buffers**, **channels**, and **selectors** that let one thread serve thousands of connections by multiplexing.

This topic covers the three core abstractions, the file I/O improvements in NIO.2, the event-driven server model selectors enable, and when you should *not* reach for NIO directly.

---

## When to use NIO

In practice you write NIO code via a framework — Netty, Vert.x, Akka, or Spring WebFlux. Each wraps the raw `java.nio` API with a more usable layer. Understanding the primitives means you can read framework code, debug a stuck server, and pick the right tool for the workload.

You'd use raw `java.nio` directly for:

- **File I/O** — `Path`, `Files`, `FileChannel` are flat-out better than `File` / `FileInputStream`.
- **Memory-mapped files** — only NIO offers `MappedByteBuffer`.
- **Specific protocols** where Netty etc. is overkill (small embedded servers).

You'd use a framework for:

- HTTP servers at scale (Netty under the hood of nearly all of them).
- Reactive applications (Project Reactor + WebFlux).
- Anything that needs proven backpressure and protocol handling.

---

## The three core abstractions

### `Buffer`

A fixed-capacity container for primitive data (most often `ByteBuffer`). Think of it as a fancy wrapper around an array with a cursor.

```java
ByteBuffer buf = ByteBuffer.allocate(1024);     // 1024 bytes
buf.put((byte) 0x41);                            // write 'A'
buf.put((byte) 0x42);                            // write 'B'
buf.flip();                                      // switch from "writing" to "reading"
byte first = buf.get();                          // read 'A'
byte second = buf.get();                         // read 'B'
buf.clear();                                     // reset for the next write cycle
```

The three numbers that matter:

| | Meaning |
|---|--------|
| `position` | Where the next `get` or `put` will happen. |
| `limit` | The end of the readable/writable window. |
| `capacity` | Total size. |

Two key state transitions:

- **`flip()`** — set `limit = position`, then `position = 0`. "I finished writing; start reading."
- **`clear()`** — set `position = 0`, `limit = capacity`. "Start writing fresh." Does *not* erase data; just resets cursors.
- **`compact()`** — keep unread bytes at the start, set `position` to after them. Used when you've partially drained a buffer and want to make room for more reads.

Forgetting to `flip()` between writing and reading is the most common `ByteBuffer` bug.

### `Channel`

A two-way connection to something that can do I/O — a file, a socket, a pipe.

```java
try (FileChannel ch = FileChannel.open(Path.of("data.txt"), StandardOpenOption.READ)) {
    ByteBuffer buf = ByteBuffer.allocate(1024);
    int n = ch.read(buf);                  // fill the buffer from the channel
    buf.flip();
    // process the buf bytes...
}
```

Common implementations:

- `FileChannel` — for files. Supports memory mapping, transfer to other channels.
- `SocketChannel` — TCP socket.
- `ServerSocketChannel` — server accept side.
- `DatagramChannel` — UDP.

Every channel can be configured non-blocking with `channel.configureBlocking(false)`. After that, `read()` returns immediately whether bytes are available or not.

### `Selector`

A multiplexer that watches many non-blocking channels and tells you which one(s) are ready right now. **One thread**, **many channels**.

```java
Selector selector = Selector.open();
ServerSocketChannel server = ServerSocketChannel.open();
server.bind(new InetSocketAddress(9000));
server.configureBlocking(false);
server.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();                       // block until at least one channel is ready
    Set<SelectionKey> keys = selector.selectedKeys();
    for (SelectionKey key : keys) {
        if (key.isAcceptable()) acceptNew(server, selector);
        else if (key.isReadable()) readSome((SocketChannel) key.channel());
    }
    keys.clear();                            // selector won't remove them for you
}
```

This is the **reactor pattern**: one thread runs the event loop; everything else is non-blocking handlers. Netty's `EventLoopGroup` is this idea, scaled up to multiple loops.

---

## NIO.2: `Path`, `Files`, `WatchService`

NIO.2 (Java 7) cleaned up file I/O. Use these instead of `java.io.File`:

```java
Path p = Path.of("data", "users.csv");
List<String> lines = Files.readAllLines(p);
Files.writeString(p, "new content");
boolean exists = Files.exists(p);
Files.walk(Path.of(".")).filter(Files::isRegularFile).forEach(System.out::println);
```

Big improvements:

- **Atomic moves** (`Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)`).
- **Symbolic links** support (`Files.createSymbolicLink`).
- **Directory walking** with `Files.walk(...)` returning a `Stream<Path>`.
- **Streaming reads** (`Files.lines(p)`) — lazy, don't load the whole file.
- **`WatchService`** — get notified when files in a directory change (OS-native; on Linux uses `inotify`).

`Path` is immutable, thread-safe, and works with paths that don't exist yet. `File` is none of those.

---

## Memory-mapped files

```java
try (FileChannel ch = FileChannel.open(Path.of("big.bin"), READ, WRITE)) {
    MappedByteBuffer mbb = ch.map(FileChannel.MapMode.READ_WRITE, 0, ch.size());
    mbb.putInt(0, 0x42);                    // writes go to the file via the OS page cache
}
```

The file becomes a region of virtual memory. Reads and writes go through the OS page cache without an explicit `read`/`write` syscall per access. Useful for:

- Random access in large files.
- High-throughput sequential I/O (Kafka uses memory mapping for log segments).
- Sharing data between processes (multiple JVMs map the same file).

Caveats:

- Mappings can be **larger than your heap** — they're virtual address space, not heap memory.
- A mapped buffer cannot be cleanly unmapped on demand — it's released when GC collects the buffer or the process exits. This is a known JVM limitation.
- On 32-bit JVMs you'll hit the 4GB address-space limit fast. Use 64-bit.

---

## Blocking vs non-blocking vs async — the three modes

| Mode | API | What happens |
|------|-----|-------------|
| **Blocking** | `java.io`, `Socket.read()` | Thread parks until data arrives. One thread per connection. |
| **Non-blocking** | `java.nio` channels + selector | Thread asks "any data?", does other work if no. One thread, many connections. |
| **Async** | `java.nio.channels.Async*` (AIO), `CompletableFuture` over NIO | OS callback when data is ready. One thread orchestrates many ops. |

Async I/O in pure Java NIO (`AsynchronousSocketChannel`) is functional but rarely used directly — Netty / WebFlux give you a better experience. Knowing it exists is enough.

---

## Common pitfalls

- **Forgetting `flip()`.** You wrote, you want to read, but `position` is past the data. `flip()` is mandatory.
- **`clear()` thinking it zeroes memory.** It only resets the cursors. Old bytes remain until overwritten.
- **Confusing `BufferedReader` with NIO.** `BufferedReader.readLine` is `java.io`, blocking. NIO equivalents need explicit decoders (`CharsetDecoder`).
- **`Selector.select()` on a thread that also does work.** A long handler stalls every other channel. Keep handlers tiny; defer real work to a worker pool.
- **Forgetting to `keys.clear()` after iteration.** The selector won't remove processed keys; you'll loop on the same set forever.
- **Mapped buffers and small files.** Memory-mapping has fixed setup cost (OS page-table changes). For a small file, plain `Files.readAllBytes` is faster.
- **Reaching for NIO when `java.io` would do.** A program that handles a dozen connections at a time doesn't need a selector loop. Use a thread pool over blocking sockets and stay simple.

---

## Code examples

1. `ByteBufferBasics.java` — write, `flip`, read, `clear`. The cursor lifecycle.
2. `FilesNio2Read.java` — read a file three ways (`readString`, `readAllLines`, `Files.lines`).
3. `FileChannelWriteRead.java` — `FileChannel` + `ByteBuffer` for raw byte I/O.
4. `MemoryMappedAccess.java` — random-access write/read via `MappedByteBuffer`.
5. `SingleThreadedNioServer.java` — minimal echo server using a `Selector` (one thread, multiple clients).

The selector server is the headline NIO demo: a single thread serving 100 concurrent clients.

---

## Try this yourself

1. In `ByteBufferBasics.java`, write 4 bytes, then read 2, then write 2 more, then read the rest. Track `position` and `limit` after each step.
2. In `SingleThreadedNioServer.java`, write a small client that opens 20 sockets to it in parallel. Confirm one server thread handles them all.
3. In `FilesNio2Read.java`, compare `Files.readAllBytes(p)` vs `Files.lines(p)` on a 100MB file. The streaming version is constant memory.

---

## Self-check

1. `buf.position` is 10 after some writes. You call `read` from a channel into `buf` without doing anything else. Bytes appear in the array — but `get()` returns garbage. Why?
2. What is the single thread doing in a selector loop, in one sentence — and why does that scale to thousands of connections when thread-per-connection breaks down at a few thousand?
3. You want random-access read/write of a 10GB file on a machine with 4GB of heap. Why does memory-mapping work for this, and where does the data actually live?
