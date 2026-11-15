# 02 — `@RestController` and `@RequestMapping`

Three controllers in one app, demonstrating the three forms of "how to return a response body" in Spring MVC.

## Run it

```bash
mvn -q spring-boot:run
```

Then in another terminal:

```bash
curl -i http://localhost:8080/api/books        # @RestController, returns JSON
curl -i http://localhost:8080/api/books/1
curl -i http://localhost:8080/legacy/books     # @Controller + @ResponseBody, returns JSON
curl -i http://localhost:8080/broken/books     # @Controller alone, fails (500)
curl -i http://localhost:8080/raw/books/1      # plain HttpServlet, same JSON written by hand
```

The first two return `200 OK` with `Content-Type: application/json` and a JSON body.
The third (`/legacy/books`) is equivalent — Spring's older verbose form.
The fourth (`/broken/books`) returns `500` (or a Whitelabel page) because Spring tries to render the return value as a view name, and there is no view resolver on the classpath.

## Run the tests

```bash
mvn -q test
```

`ServletFlowTest` starts real Tomcat and shows the request path: a filter (`RequestPathFilter`) runs before both the raw servlet (`RawBookServlet` at `/raw/books/*`) and the `DispatcherServlet` (`/`), and the raw servlet returns the same JSON the controller does — only because it was coded by hand.

`BookControllerTest` uses MockMvc to assert the three controllers' behaviours without starting Tomcat. MockMvc gets a proper introduction in topic 11; treat the assertions as readable English for now.
