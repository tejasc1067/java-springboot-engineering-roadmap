# 05 — Query parameters

`@RequestParam` in five shapes: required, defaulted, optional, list, all-params.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i "http://localhost:8080/api/books?author=Bloch"
curl -i "http://localhost:8080/api/books"
curl -i "http://localhost:8080/api/books?page=1&size=2"
curl -i "http://localhost:8080/api/books?tag=java&tag=spring"
curl -i "http://localhost:8080/api/books?tag=java,spring"
curl -i "http://localhost:8080/api/debug?foo=1&bar=2&baz=3"
curl -i "http://localhost:8080/api/search"         # 400 - 'author' is required
curl -i "http://localhost:8080/api/books?page=abc" # 400 - int conversion fails
```

## Run the tests

```bash
mvn -q test
```
