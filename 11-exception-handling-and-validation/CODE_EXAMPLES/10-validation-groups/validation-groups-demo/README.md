# 10 — Validation groups

One DTO (`BookPayload`), validated differently per operation. `id` is `@Null` on create
(`OnCreate` group) and `@NotNull` on update (`OnUpdate` group); `title` is always required.
`@Valid` can't name a group, so the controller uses Spring's `@Validated({Default.class, OnCreate.class})`
on POST and `@Validated({Default.class, OnUpdate.class})` on PUT.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# create WITH an id -> 400, id must be absent on create
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"id":5,"title":"Clean Code"}'

# create without id, valid -> 201 (server assigns id 42)
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Clean Code"}'

# update WITHOUT id in body -> 400, id required on update
curl -i -X PUT http://localhost:8080/api/books/1 -H "Content-Type: application/json" \
     -d '{"title":"Clean Code"}'

# update with id, valid -> 200
curl -i -X PUT http://localhost:8080/api/books/1 -H "Content-Type: application/json" \
     -d '{"id":1,"title":"Clean Code"}'
```

The two 400s carry a `fieldErrors` array naming `id`.

## Run the tests

```bash
mvn -q test
```

Drop `Default.class` from the POST handler — make it `@Validated(OnCreate.class)` — and re-run.
The `createWithIdIsRejected` test still passes, but a blank `title` now slips through: `title`
lives in the `Default` group, which is no longer requested. That is the default-group subtlety.
