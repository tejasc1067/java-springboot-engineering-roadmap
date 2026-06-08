# What REST is (and isn't)

Before you write a single `@RestController`, you need to know what REST is — because most APIs that *call themselves* REST are not. Knowing the difference will save you arguments with frontend engineers, save you from URL designs you later regret, and let you read the rest of this module with the right mental model.

This topic is the only markdown-only topic in module 09. There is no code project; the lesson is conceptual. From topic 02 onward, every page comes with a runnable Spring Boot demo.

---

## The problem this solves

You have heard "we have a REST API" thousands of times. It means roughly nothing on its own, because the same phrase is used for:

1. APIs where every request is `POST /api/doAction` and the body decides what to do.
2. APIs where URLs map to **resources** (`/users/42`, `/books`) and HTTP **verbs** decide what to do.
3. APIs that look like (2) but quietly keep server-side session state across calls.
4. APIs that look like (2) and *also* embed hyperlinks to related resources in every response (full HATEOAS — almost nobody does this).

Roy Fielding's 2000 dissertation defines REST strictly — most APIs you will write are at level (2), which is sometimes called "RESTful" rather than "REST." That is fine. The naming purity matters less than knowing which of those four styles you are actually building and why.

This module teaches the (2) style — resource-oriented, stateless, verb-driven JSON over HTTP. That is what 95% of Spring Boot services are.

---

## The five rules in plain English

REST is a set of constraints on top of HTTP. The ones that matter on day one:

### 1. Resources, not actions

A REST URL names a **thing**, not a verb. The verb is the HTTP method.

```text
Bad  (RPC style):   POST /api/createBook          POST /api/getBookById
                    POST /api/updateBookTitle     POST /api/deleteBook

Good (REST style):  POST /api/books               GET  /api/books/42
                    PUT  /api/books/42            DELETE /api/books/42
```

If you find yourself putting a verb in the URL (`/login`, `/sendEmail`, `/processPayment`), pause. Sometimes that is fine — actions that don't map to a single resource (`POST /sessions` for login, `POST /payments` for "create a payment") can be modeled as resources too. The rule is: **default to nouns; reach for verbs only when no noun fits.**

### 2. HTTP verbs do the work

| Verb | Means | Idempotent? | Safe? |
|------|-------|-------------|-------|
| GET    | read a resource             | yes | yes (no side effects) |
| POST   | create a new resource       | no  | no  |
| PUT    | replace a resource entirely | yes | no  |
| PATCH  | partially update a resource | no* | no  |
| DELETE | remove a resource           | yes | no  |

**Idempotent** means: doing the same request twice has the same effect as doing it once. `DELETE /books/42` twice still leaves book 42 deleted. `POST /books` twice creates two books. This matters because clients (and proxies) sometimes retry on timeout — idempotent operations are safe to retry blindly; non-idempotent ones are not.

*PATCH can be designed idempotently (e.g. "set field X to Y" is idempotent — same input, same final state) but is not required to be (e.g. "append to list" is not). When in doubt, treat it as non-idempotent.

**Safe** means: the request does not change server state. A GET should never delete or mutate anything. This isn't decoration — search-engine crawlers, browser pre-fetchers, and proxies all assume safety. A GET that deletes things will eventually be triggered by something you didn't expect.

### 3. Stateless server

Each request carries everything the server needs to handle it. The server does **not** remember the previous request from this client.

That does not mean "no authentication" — it means **no server-side session.** The client sends a token (e.g. in an `Authorization: Bearer ...` header) on every request, and the server resolves it independently each time. Sticky-session load balancing, in-memory session maps keyed by `JSESSIONID` — both fight against the REST model.

In practice: when you have to scale your service to ten instances behind a load balancer, statelessness is the property that lets you do it without thinking. Any instance can handle any request.

### 4. A useful, predictable status code on every response

The HTTP status code is part of the API contract, not decoration.

- `200 OK` for a successful read or update with a body.
- `201 Created` for a successful POST that produced a new resource — with a `Location` header pointing at it.
- `204 No Content` for a successful operation with no body (e.g. DELETE).
- `400 Bad Request` for malformed input.
- `401 Unauthorized` (badly named — should be "Unauthenticated") for missing/invalid credentials.
- `403 Forbidden` for authenticated but not allowed.
- `404 Not Found` for missing resources.
- `409 Conflict` for "you can't do that right now" (e.g. unique-key violation, optimistic-lock failure).
- `422 Unprocessable Entity` for syntactically valid input that semantically fails business rules.
- `500 Internal Server Error` for unexpected exceptions on the server.

A REST API that returns `200 OK` for every response and signals failure inside the JSON body (`{"success": false, "error": "..."}`) is fighting against the protocol. Topic 08 has the full decision tree.

### 5. JSON in, JSON out (in practice)

The REST spec is content-type-agnostic — XML, HTML, MessagePack, protobuf-over-HTTP all count. In the Java/Spring world in 2026, the overwhelming default is JSON. Spring Boot wires Jackson by default; topic 06 shows the binding.

---

## REST vs RPC — the URL test

The fastest way to tell a REST API from an RPC-style one is to look at the URLs. RPC names actions; REST names resources.

| RPC-style                                | REST equivalent                |
|------------------------------------------|--------------------------------|
| `POST /api/getUserById`                  | `GET /api/users/{id}`          |
| `POST /api/createUser`                   | `POST /api/users`              |
| `POST /api/updateUserEmail`              | `PATCH /api/users/{id}`        |
| `POST /api/deleteUser`                   | `DELETE /api/users/{id}`       |
| `POST /api/listOrdersForUser`            | `GET /api/users/{id}/orders`   |
| `POST /api/searchBooksByAuthor`          | `GET /api/books?author=Bloch`  |

RPC-style isn't *wrong* — gRPC is RPC and it works fine. But if you call it REST and design it like RPC, every consumer who expected REST will be confused. Pick one and be consistent.

---

## Things people get wrong about REST

### "REST means JSON"

No. REST is a set of architectural constraints. JSON is just the dominant payload format in the modern web. REST predates JSON's dominance by years.

### "REST means stateless authentication"

No. Stateless **session** — meaning the server does not keep per-client state between requests. Authentication can absolutely happen on every request via a token; that token resolves to a user identity stateless-ly.

### "REST APIs must use plural nouns"

Convention, not rule. `/api/users` is more common than `/api/user`. Pick one and be consistent across your whole API. Inconsistent pluralization is the single most-cursed thing in API design — `/users` and `/order` in the same service is a smell.

### "REST APIs must be at HATEOAS level"

Fielding's strict reading says yes — links between resources are part of REST. In practice, almost no Java backend in production does full HATEOAS, because the cost (designing link relations, teaching clients to follow them) is high and the benefit (clients that don't hardcode URLs) rarely materialises. This module does not teach HATEOAS. Your career will likely never require it.

### "GET requests with bodies are fine"

Technically allowed in HTTP/1.1, practically broken. Proxies, CDNs, server frameworks, and `fetch()` in browsers all treat GET bodies inconsistently. If you need to send a structured query, either encode it in query parameters or change the verb to POST. Topic 05 covers query parameter patterns.

---

## A worked example: designing a books API

Pretend you're building a service to manage a library's book catalogue. Here is the resource design before you write any Spring code:

```text
GET    /api/books                 list books (with paging/filtering — topic 12)
POST   /api/books                 create a new book; 201 + Location header
GET    /api/books/{id}            fetch one; 200 or 404
PUT    /api/books/{id}            replace the whole book; 200 or 204
PATCH  /api/books/{id}            partial update (e.g. just the title); 200 or 204
DELETE /api/books/{id}            delete; 204 or 404

GET    /api/books/{id}/reviews    list reviews for a book (subresource)
POST   /api/books/{id}/reviews    add a review; 201 + Location header
```

Notice what is *not* in that list:

- No `GET /api/getBooks` — the URL is the noun, the verb is in the method.
- No `POST /api/updateBookTitle/{id}` — partial updates are PATCH, full replacement is PUT, and the body says what changed.
- No `POST /api/books/search` — search is filtering on the collection, encoded as query parameters.
- No `GET /api/books/42/delete` — deletes are DELETE; GETs are safe.

This shape is what topics 02–12 will build, one mechanic at a time.

---

## When to use it / when not to

REST is the right default for HTTP APIs serving a frontend or other services in the same organization. It is well-understood, debuggable with `curl`, cacheable at HTTP layer, and almost every frontend tool speaks it natively.

Reach for **something else** when:

- You need **streaming** or **bidirectional** communication (push from server to client) — WebSockets or Server-Sent Events.
- You need **typed schemas with code generation** for many languages — gRPC.
- The frontend wants to **shape the response** (pick which fields) — GraphQL.
- You're modelling **events**, not resource state — Kafka topics or an event-driven design (module 17).

REST is a good fit for "client wants to read or modify a record." It is a worse fit for "server wants to push live updates to many subscribers."

---

## Common pitfalls

- **Putting verbs in URLs.** `/getUser/42` is RPC dressed as REST. Use `GET /users/42`.
- **Using POST for everything.** "I'll just POST and put the action in the body." This works but loses caching, retry safety, and consumer expectations.
- **Returning 200 with `{"error": ...}`.** Use HTTP status codes — they are the protocol. Topic 08 explains which one when.
- **Pluralizing inconsistently.** `/users` and `/order` side-by-side is a never-ending source of bugs in client code.
- **Holding server-side session state.** Breaks horizontal scaling, breaks idempotency, breaks the mental model. Use stateless auth tokens.
- **Big nested paths.** `/companies/7/departments/3/teams/12/members/42` becomes painful fast. Two levels deep is plenty; beyond that, use query parameters.

---

## Try this yourself

1. Take a SOAP, RPC, or just-confused API you have seen at work and rewrite five of its endpoints as REST URLs. Note where you had to invent a noun.
2. Sketch the URL design for a "todo list" API: list/create/read/update/delete todos, mark a todo done, list todos belonging to a user. Decide where to use POST vs PUT vs PATCH.
3. Find a public REST API (GitHub's, Stripe's, Twilio's) and read three endpoints. Note one thing they do well and one design choice you'd argue with.

## Self-check

1. What is the difference between **idempotent** and **safe**? Give a verb that is one but not the other.
2. Why is `POST /api/getUser` not REST, and what is the REST equivalent?
3. A client retries `DELETE /api/books/42` after a network timeout. Is that safe to do? Why?
4. Your colleague proposes returning `200 OK` with `{"ok": false, "error": "Book not found"}` instead of `404`. Give two concrete reasons not to.
