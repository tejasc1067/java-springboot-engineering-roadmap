# 14 — API versioning

Three strategies in one app: URI versioning, header versioning, media-type versioning.

## Run it

```bash
mvn -q spring-boot:run
```

URI versioning:

```bash
curl -s http://localhost:8080/api/v1/books
curl -s http://localhost:8080/api/v2/books
```

Header versioning:

```bash
curl -s -H "X-API-Version: 1" http://localhost:8080/api/header-books
curl -s -H "X-API-Version: 2" http://localhost:8080/api/header-books
```

Media-type versioning:

```bash
curl -s -H "Accept: application/vnd.example.v1+json" http://localhost:8080/api/media-books
curl -s -H "Accept: application/vnd.example.v2+json" http://localhost:8080/api/media-books
```

## Run the tests

```bash
mvn -q test
```
