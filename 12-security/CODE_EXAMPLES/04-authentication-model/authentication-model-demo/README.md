# 04 — The authentication model (database-backed users)

Users now live in an H2 database instead of the generated in-memory user. A `JpaUserDetailsService` loads them, a `BCryptPasswordEncoder` verifies passwords, and Spring wires the two together into a `DaoAuthenticationProvider` automatically.

Two users are seeded at startup: `alice` / `password` and `bob` / `s3cret` — but the database stores only the BCrypt *hash* of each.

## Run it

```bash
mvn -q spring-boot:run
```

There is **no** "Using generated security password" line now — defining a `UserDetailsService` bean makes Spring Boot stop creating the default user. The database is the only source of logins.

```bash
curl -i http://localhost:8080/api/books
# HTTP/1.1 401                              <- anonymous, rejected before the controller

curl -i -u alice:password http://localhost:8080/api/books
# HTTP/1.1 200   [{"id":1,...}]             <- DB user, password verified against the stored hash

curl -i -u alice:wrong http://localhost:8080/api/books
# HTTP/1.1 401                              <- password doesn't match the hash

curl -i -u nobody:password http://localhost:8080/api/books
# HTTP/1.1 401                              <- unknown user (reported as bad credentials, not "no such user")
```

Want to see the stored hash? Enable the H2 console or add `show-sql` + a query — the `password` column holds something like `$2a$10$....`, never `password`.

## Run the tests

```bash
mvn -q test
```

`AuthenticationModelTest` drives the full stack with `TestRestTemplate` and asserts: anonymous → 401, `alice`/`password` → 200, wrong password → 401, unknown user → 401, and that the stored password is a BCrypt hash (not plaintext).
