# 03 — Configuring the SecurityFilterChain

Topic 02's default locked *everything*. Here we define our own `SecurityFilterChain` bean (`SecurityConfig`) so `/public/**` is open and `/api/**` requires a login — and Spring Boot backs off its default chain the moment our bean exists.

## Run it

```bash
mvn -q spring-boot:run
```

Copy the generated password from the startup log (username `user`), then:

```bash
curl -i http://localhost:8080/public/health
# HTTP/1.1 200        {"status":"UP"}          <- permitAll(), no credentials needed

curl -i http://localhost:8080/api/books
# HTTP/1.1 401        WWW-Authenticate: Basic  <- authenticated(), and you're anonymous

curl -i -u user:<paste-generated-password> http://localhost:8080/api/books
# HTTP/1.1 200        [{"id":1,...}]           <- authenticated via HTTP Basic
```

Because this chain enables **HTTP Basic only** (no form login), an anonymous caller always gets a clean `401` — even a browser — instead of being redirected to a login page. That's the topic-02 behavior changed on purpose.

## Run the tests

```bash
mvn -q test
```

`FilterChainTest` uses `spring-security-test` to run the real filter chain under MockMvc and asserts:
`/public/health` → 200 anonymous, `/api/books` → 401 anonymous, `/api/books` → 200 with `.with(user("alice"))`.
