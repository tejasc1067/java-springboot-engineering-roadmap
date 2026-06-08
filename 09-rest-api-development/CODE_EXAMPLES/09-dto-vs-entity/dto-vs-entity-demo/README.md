# 09 — DTO vs entity

Three wire shapes (CreateUserRequest, UserResponse) wrapping one domain object (User with passwordHash). The password hash never crosses the wire.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# Create
curl -i -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username":"alice","password":"hunter2"}'

# Mass-assignment attempt -- server-set fields in the body are ignored
curl -i -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username":"bob","password":"x","passwordHash":"evil","createdAt":"1970-01-01T00:00:00Z"}'

# Read
curl -i http://localhost:8080/api/users/1
```

Inspect the response: `username`, `id`, `createdAt`. No `passwordHash`, ever.

## Run the tests

```bash
mvn -q test
```
