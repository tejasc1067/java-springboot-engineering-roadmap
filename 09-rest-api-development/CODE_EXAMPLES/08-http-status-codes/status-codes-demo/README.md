# 08 — HTTP status codes

One endpoint per common status code. `curl` each one and see how the response differs.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i http://localhost:8080/api/status/ok                  # 200
curl -i http://localhost:8080/api/status/created             # 201 + Location
curl -i http://localhost:8080/api/status/accepted            # 202
curl -i http://localhost:8080/api/status/no-content          # 204 (empty body)
curl -i http://localhost:8080/api/status/bad-request         # 400
curl -i http://localhost:8080/api/status/unauthorized        # 401
curl -i http://localhost:8080/api/status/forbidden           # 403
curl -i http://localhost:8080/api/status/not-found           # 404
curl -i http://localhost:8080/api/status/conflict            # 409
curl -i http://localhost:8080/api/status/unprocessable       # 422
curl -i http://localhost:8080/api/status/server-error        # 500
curl -i http://localhost:8080/api/status/unavailable         # 503
curl -i -X PUT http://localhost:8080/api/status/ok           # 405 (only GET defined)
```

## Run the tests

```bash
mvn -q test
```
