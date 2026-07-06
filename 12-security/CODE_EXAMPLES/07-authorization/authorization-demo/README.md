# 07 — Authorization (URL rules + method security)

Two users — `alice` (`ROLE_USER`) and `admin` (`ROLE_ADMIN`) — both with password `password`. Three authorization styles in one app:

- **URL-based**: `/api/admin/**` requires `ADMIN` (in `SecurityConfig`).
- **Method-based**: `DELETE /api/books/{id}` is annotated `@PreAuthorize("hasRole('ADMIN')")`.
- **Expression-based**: `GET /api/users/{username}/profile` allows `ADMIN` *or* the user viewing their own profile.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# any authenticated user can read
curl -i -u alice:password http://localhost:8080/api/books            # 200

# URL-based admin rule
curl -i -u alice:password http://localhost:8080/api/admin/stats      # 403  (authenticated, wrong role)
curl -i -u admin:password http://localhost:8080/api/admin/stats      # 200

# method-based @PreAuthorize
curl -i -u alice:password -X DELETE http://localhost:8080/api/books/1  # 403
curl -i -u admin:password -X DELETE http://localhost:8080/api/books/1  # 200

# expression-based ownership
curl -i -u alice:password http://localhost:8080/api/users/alice/profile  # 200 (own)
curl -i -u alice:password http://localhost:8080/api/users/bob/profile    # 403 (someone else's)
curl -i -u admin:password http://localhost:8080/api/users/bob/profile    # 200 (admin sees any)

# anonymous
curl -i http://localhost:8080/api/books                              # 401 (not 403 — you're not logged in at all)
```

The `401` vs `403` split is the whole point: **401 = we don't know who you are; 403 = we know, and you may not do this.**

## Run the tests

```bash
mvn -q test
```

`AuthorizationTest` (9 cases) covers each style for both roles, plus anonymous → 401.
