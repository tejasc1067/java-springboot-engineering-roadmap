# OpenAPI and Swagger — auto-generated API docs

Real Spring Boot services hand the frontend team a generated API specification, not a Word document. The standard format is **OpenAPI 3** (formerly Swagger), and the standard tool in the Java world is **springdoc-openapi** — a Spring Boot starter that reads your controllers and produces the spec and an interactive UI without you writing anything.

---

## The problem this solves

Three things people used to do, and stopped:

1. Write API docs by hand in a wiki. They were wrong by the second deploy.
2. Use Swagger 1.x with annotations on every method. Verbose, drift-prone.
3. Manually publish a Postman collection. Better than nothing, still drifts.

OpenAPI is a JSON/YAML schema describing every endpoint, every request shape, every response shape, every status code. Generated *from your code*, it cannot drift. Swagger UI renders that JSON as an interactive page where you click "Try it out" and the request actually fires.

Adding all of this to a Spring Boot service is two lines of `pom.xml`.

---

## How it works

### 1. Add the dependency

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.17</version>
</dependency>
```

Just that. No Java config, no `@EnableOpenApi`. Spring Boot autoconfiguration picks it up at startup.

### 2. What you get for free

Two endpoints become available:

| URL | Content |
|-----|---------|
| `/v3/api-docs`         | The OpenAPI 3 spec as JSON |
| `/v3/api-docs.yaml`    | The same spec as YAML |
| `/swagger-ui.html`     | The interactive UI |

Springdoc walks your controllers. For each `@RequestMapping` / `@GetMapping` / etc. method it sees, it generates an entry: path, verb, expected request shape (from `@RequestBody` DTO), expected responses, parameter list. The DTO schemas are derived from Jackson — so any `@JsonProperty`, `@JsonIgnore`, `@JsonFormat` is honored.

For a controller from topic 03 (`@GetMapping("/{id}") Book one(@PathVariable Long id)`), the spec already includes:

- Path: `/api/books/{id}`
- Method: GET
- Path parameter `id` of type `integer` (or `string` if Spring can't infer)
- Response 200: schema = the `Book` record
- (Implicit) Response 400: type-mismatch on the path variable

You did nothing extra. That is the headline.

### 3. Enhance the docs with annotations (when you want to)

Auto-generated docs are good. Annotated docs are excellent. The four annotations that earn their weight:

```java
@Tag(name = "Books", description = "Catalog operations")
@RestController
@RequestMapping("/api/books")
public class BookController {

    @Operation(summary = "List all books",
               description = "Returns the catalog with optional filters.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List returned"),
        @ApiResponse(responseCode = "400", description = "Invalid filter value")
    })
    @GetMapping
    public List<Book> all(@RequestParam(required = false) String author) { ... }

    @Operation(summary = "Find one book by id")
    @GetMapping("/{id}")
    public ResponseEntity<Book> one(
            @Parameter(description = "Book id", example = "42")
            @PathVariable Long id) { ... }
}
```

And on the DTO:

```java
public record Book(
        @Schema(description = "Server-assigned identifier", example = "42")
        Long id,

        @Schema(description = "Title, 1-200 chars", example = "Effective Java")
        String title,

        @Schema(description = "Author full name", example = "Joshua Bloch")
        String author) {}
```

These annotations carry through to the UI — every field, every endpoint, gets a one-line description.

### 4. Other useful knobs

In `application.yml`:

```yaml
springdoc:
  api-docs:
    path: /api-docs            # change the JSON endpoint path
  swagger-ui:
    path: /docs                # change the UI path
    operations-sorter: method  # group by method on the page
    tags-sorter: alpha
```

For production, you usually **disable** Swagger UI but keep the spec endpoint:

```yaml
springdoc:
  swagger-ui:
    enabled: false             # no UI in production
  api-docs:
    enabled: true              # keep the spec for tooling
```

The reason: an open `/swagger-ui.html` lists every endpoint your service exposes — useful for an attacker mapping your API.

---

## What the response looks like

`GET /v3/api-docs` returns an OpenAPI 3 document. The shape (heavily truncated):

```json
{
  "openapi": "3.0.1",
  "info": { "title": "OpenAPI definition", "version": "v0" },
  "paths": {
    "/api/books/{id}": {
      "get": {
        "tags": ["Books"],
        "summary": "Find one book by id",
        "parameters": [{
          "name": "id",
          "in": "path",
          "required": true,
          "schema": { "type": "integer", "format": "int64" }
        }],
        "responses": {
          "200": { "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Book" } } } }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "Book": {
        "type": "object",
        "properties": {
          "id":     { "type": "integer", "format": "int64" },
          "title":  { "type": "string" },
          "author": { "type": "string" }
        }
      }
    }
  }
}
```

You can hand that file to any code generator (openapi-generator, openapi-typescript) and produce client SDKs in TypeScript, Kotlin, Swift, Go — whatever language your consumers use.

---

## Common pitfalls

- **Bringing springdoc into production without disabling the UI.** Surface-area leak. Disable `swagger-ui.enabled` (or guard it behind security).
- **Drift between annotation and behaviour.** Springdoc reads what your code *says*, not what it *does*. An annotated `@ApiResponse(responseCode = "404")` that no path can actually produce is a lie that auto-generated docs cannot detect.
- **Generic types lost to type-erasure.** A method returning `ResponseEntity<List<Book>>` may resolve to `array of object` rather than `array of Book` if the generic context confuses introspection. Usually fine; if it bites, add `@Schema(implementation = Book.class)` or split into a named DTO.
- **Mixing v3 and v2 dependencies.** Old projects sometimes still have `springfox-swagger2`. That library is unmaintained since 2020. Don't mix; pick one (`springdoc-openapi` is the modern choice) and remove the other.
- **Documenting authentication you haven't implemented.** Add `@SecurityRequirement(name = "bearerAuth")` and a corresponding `securitySchemes` declaration only when security is actually in place (module 12).

---

## Try this yourself

The demo project is `CODE_EXAMPLES/13-openapi-and-swagger/openapi-demo`.

1. Run `mvn -q spring-boot:run`, then visit:
   - `http://localhost:8080/swagger-ui.html` — interactive UI.
   - `http://localhost:8080/v3/api-docs` — raw JSON spec.

2. In Swagger UI, expand `GET /api/books/{id}`, click "Try it out", enter `1`, click "Execute". See the request fire and the response come back.

3. Remove every springdoc annotation from `BookController` and `Book`. Re-run, re-visit `/v3/api-docs`. The endpoints are *still there* — the annotations were enhancements, not requirements. The descriptions are gone, but the schema lives on.

4. Add a new endpoint without any annotations. Refresh Swagger UI. It appears automatically.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — record with `@Schema` annotations on fields.
3. **`BookController.java`** — `@Tag`, `@Operation`, `@Parameter`, `@ApiResponses`.
4. **`OpenApiDocsTest.java`** — MockMvc verifies `/v3/api-docs` returns JSON and includes the expected paths.

## Self-check

1. What two URLs does springdoc expose by default after you add the starter?
2. You have written `@Operation(summary = "...")` and `@Schema(description = "...")` annotations all over the code. A new developer wonders: do they affect the *behavior* of the API or only the *documentation*?
3. Why would a production deployment turn off `springdoc.swagger-ui.enabled` while keeping `springdoc.api-docs.enabled` on?
