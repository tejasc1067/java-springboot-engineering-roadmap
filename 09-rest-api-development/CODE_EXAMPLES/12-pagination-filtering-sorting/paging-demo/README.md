# 12 — Pagination, filtering, sorting

Hand-rolled filter + sort + page against an in-memory list. The wire shape matches Spring Data's `Page<T>`.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -s "http://localhost:8080/api/books?page=0&size=2"
curl -s "http://localhost:8080/api/books?author=Bloch"
curl -s "http://localhost:8080/api/books?sort=year,asc"
curl -s "http://localhost:8080/api/books?sort=author,asc&sort=year,desc"
curl -s "http://localhost:8080/api/books?author=Bloch&page=0&size=2&sort=year,desc"

# Hostile size -> clamped
curl -s "http://localhost:8080/api/books?size=1000000"

# Unknown sort field -> 400
curl -s "http://localhost:8080/api/books?sort=secret,asc"
```

## Run the tests

```bash
mvn -q test
```
