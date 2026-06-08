# Request body — `@RequestBody` and Jackson

`@RequestBody` is the annotation that turns a JSON payload into a Java object. The thing doing the actual conversion is **Jackson** — the JSON library Spring Boot wires by default. This topic covers the binding, the choice of records vs classes for DTOs, and the four Jackson annotations you will see in real code.

---

## The problem this solves

In topic 03 you wrote:

```java
@PostMapping
public Book create(@RequestBody Book incoming) { ... }
```

A client posts JSON. Spring needs to turn the bytes into a `Book` instance before your method body runs. That conversion is **deserialization**, and Spring delegates it to Jackson.

You did nothing to set this up. `spring-boot-starter-web` brought in `jackson-databind`, and Spring Boot autoconfigured a `MappingJackson2HttpMessageConverter` that handles every `Content-Type: application/json` request and response. The default is "make it work."

But the defaults are not always what you want — the JSON might use snake_case while your Java uses camelCase, some fields should be hidden from input, dates need a specific format. That's what the Jackson annotations are for.

---

## How it works — the happy path

```java
record Book(Long id, String title, String author) {}

@PostMapping
public Book create(@RequestBody Book incoming) {
    return incoming;
}
```

A client posts:

```http
POST /api/books
Content-Type: application/json

{ "id": null, "title": "Effective Java", "author": "Bloch" }
```

Jackson:

1. Reads the body as JSON.
2. Sees you want a `Book`.
3. Looks at the record's components (`id`, `title`, `author`) — the names match the JSON keys, the types are compatible.
4. Calls the canonical record constructor with the matched values.
5. Hands the `Book` instance to your method.

Records work especially well as DTOs because they are immutable, have a canonical constructor, and Jackson supports them natively since `jackson-databind` 2.12. No special annotations needed for the simple case.

---

## When the defaults aren't enough — four annotations

### `@JsonProperty` — rename a field

Frontends often use snake_case. Your Java uses camelCase. Bridge them on the field:

```java
record Book(
        Long id,
        String title,
        String author,
        @JsonProperty("published_year") int publishedYear) {}
```

Now the JSON is `"published_year": 1999` but the Java field is `publishedYear`. The same annotation works on serialization too — the response will spell it `published_year`.

If your whole API uses snake_case, set the global policy instead of annotating every field:

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
```

Then drop the per-field `@JsonProperty`.

### `@JsonIgnore` — hide a field from JSON

```java
record User(Long id, String username, @JsonIgnore String passwordHash) {}
```

`passwordHash` will not be serialized in responses, nor accepted in requests. This is the simplest way to keep sensitive fields out of the wire shape — but the cleaner pattern in real apps is to split into a separate response DTO (topic 09), not paper over the domain object with `@JsonIgnore`.

### `@JsonCreator` — explicit constructor selection

For records with one canonical constructor, Jackson finds it automatically. For classes with multiple constructors or for non-record DTOs, point Jackson at the right one:

```java
public class Money {
    private final long cents;
    private final String currency;

    @JsonCreator
    public Money(@JsonProperty("amount") long cents,
                 @JsonProperty("currency") String currency) {
        this.cents = cents;
        this.currency = currency;
    }
    // getters...
}
```

You will need this less often if you prefer records.

### `@JsonFormat` — date/time format

Jackson serializes `LocalDate` as ISO-8601 by default (`"2025-12-31"`) — usually fine. When the API contract demands a specific format:

```java
record Event(
        Long id,
        String name,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate when) {}
```

Now the client sends `"31/12/2025"` and Jackson parses it. Prefer the default ISO-8601 unless you have a reason; consistency across your API matters more than per-field formats.

---

## Things to know about the defaults

### Missing fields become null (or default)

Send `{ "title": "EJ" }` to a `record Book(Long id, String title, String author)`. Jackson does *not* complain. The `id` and `author` parameters get `null`. This is why **validation** (topic 10) matters — `@RequestBody` alone trusts the client.

### Unknown fields are ignored by default

Send `{ "title": "EJ", "secret": "evil" }`. Jackson ignores `secret`. To make unknown fields fail loudly:

```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: true
```

Or, per-DTO:

```java
@JsonIgnoreProperties(ignoreUnknown = false)
record Book(...) {}
```

Strict failure is safer if your API contract is locked down; relaxed (the default) is better for backwards compatibility while you add fields.

### `null` vs missing

Jackson cannot distinguish `{"author": null}` from `{}` once it hits a record — both result in `author = null`. If you need to tell them apart (e.g. for PATCH semantics), use `Optional<String>` or `JsonNullable<String>` (from the `jackson-databind-nullable` extension). For most APIs, you don't need that distinction.

### Content-Type matters

Jackson fires on `Content-Type: application/json`. If a client sends `Content-Type: text/plain`, Spring returns `415 Unsupported Media Type` and your method is never called. Always set the header on the client.

---

## Records vs Lombok `@Data` classes — what to use

Records are the modern default for DTOs:

```java
record CreateBookRequest(String title, String author) {}
```

Lombok-style classes still appear in older codebases:

```java
@Data           // generates getters, setters, equals, hashCode, toString
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookRequest {
    private String title;
    private String author;
}
```

Both work with Jackson. Records win on:

- **Immutability by default** — no setter, no accidental mutation after deserialization.
- **No annotation processor** — records are part of the language since Java 16. Lombok needs the IDE and build to cooperate.
- **Less code** — three lines instead of a class block.

This roadmap uses records throughout. If you join a Lombok-using codebase, both shapes deserialize the same — Jackson does not care which one you give it.

---

## Common pitfalls

- **Forgetting `Content-Type: application/json`.** Result: 415 from Spring. Always set the header on POST/PUT/PATCH.
- **Sending fields that the record does not declare.** Default: silently ignored. Set `fail-on-unknown-properties: true` if you want loud failure.
- **No constructor visible to Jackson.** Records have one automatically. A regular class with only field initialization but no constructor (and no setters) will fail with `InvalidDefinitionException`. Add a constructor or use Lombok `@AllArgsConstructor`.
- **Mismatched names without `@JsonProperty`.** `{"firstName": "Bob"}` and `record User(String first_name)` won't bind. Either match the names or annotate.
- **Returning the request DTO directly.** Tempting — and fine for the demo above — but real APIs separate input shape from output shape. Topic 09 covers DTO discipline.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/06-request-body-and-jackson/request-body-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: application/json" \
        -d '{"title":"Effective Java","author":"Bloch","published_year":2017}'

   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: application/json" \
        -d '{"title":"Clean Code"}'                  # author and year missing -> null/0

   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: application/json" \
        -d '{"title":"X","unknown":"field"}'         # unknown field silently ignored

   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: text/plain" \
        -d 'not json'                                # 415 Unsupported Media Type
   ```

2. Flip `fail-on-unknown-properties` to `true` in `application.yml`. Repeat the unknown-field call and watch it now return 400.

3. Change `@JsonProperty("published_year")` to `@JsonProperty("year")`. Note the request key has to change to match.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — record DTO with one renamed field.
3. **`BookController.java`** — `@RequestBody Book incoming`. Jackson does the rest.
4. **`BookControllerTest.java`** — MockMvc assertions covering happy path, missing field, unknown field, wrong content-type.

## Self-check

1. A client posts `{"title": "EJ"}` to a `record Book(Long id, String title, String author)`. What are the values of `id` and `author` after Jackson binds the request?
2. What status does Spring return when the client sets `Content-Type: text/plain`, and why?
3. You want the JSON wire to use `snake_case` but your Java to keep `camelCase`. What are two ways to achieve that, and when would you pick one over the other?
