# 02 — Spring Security defaults

An ordinary open controller. Add `spring-boot-starter-security` (see the `pom.xml`) and — with zero security code written — every endpoint now demands authentication, guarded by an auto-generated password.

## Run it

```bash
mvn -q spring-boot:run
```

Watch the startup log for a line like:

```
Using generated security password: 3f2a9c7e-1b8d-4a2e-9f0c-6d5b4a3c2e1f

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

That password is **regenerated on every restart**. The default username is `user`. Now call the API:

```bash
curl -i http://localhost:8080/api/books
# HTTP/1.1 401
# WWW-Authenticate: Basic realm="Realm"      <- the filter chain rejected you before the controller ran

curl -i -u user:<paste-the-generated-password> http://localhost:8080/api/books
# HTTP/1.1 200
# [{"id":1,"title":"The Pragmatic Programmer",...}]

curl -i -u user:wrong http://localhost:8080/api/books
# HTTP/1.1 401                                <- wrong credentials, still rejected
```

Open `http://localhost:8080/api/books` in a browser instead and you get a **login form** rather than a `401` — same default chain, different entry point chosen because a browser sends `Accept: text/html`.

## Run the tests

```bash
mvn -q test
```

The test pins a known user (`demo`/`demo`) via properties so it's deterministic, then asserts the three cases: anonymous → 401, wrong password → 401, correct Basic credentials → 200.
