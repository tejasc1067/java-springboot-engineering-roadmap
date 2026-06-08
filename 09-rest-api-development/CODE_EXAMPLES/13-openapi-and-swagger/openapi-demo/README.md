# 13 — OpenAPI / Swagger UI with springdoc

Adds `springdoc-openapi-starter-webmvc-ui` 2.8.17 to a Spring Boot service. The `/v3/api-docs` spec and `/swagger-ui.html` UI light up automatically.

## Run it

```bash
mvn -q spring-boot:run
```

Then open:

- `http://localhost:8080/swagger-ui.html` — interactive UI
- `http://localhost:8080/v3/api-docs` — raw OpenAPI JSON

## Run the tests

```bash
mvn -q test
```

The test calls `/v3/api-docs` via MockMvc and asserts the expected paths and schemas are present.
