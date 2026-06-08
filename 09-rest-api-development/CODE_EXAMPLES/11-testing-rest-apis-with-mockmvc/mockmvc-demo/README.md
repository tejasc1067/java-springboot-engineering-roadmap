# 11 — Testing REST APIs with MockMvc

One controller, one service, two test classes -- full-context and slice.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i http://localhost:8080/api/books
curl -i http://localhost:8080/api/books/1
```

## Run the tests

```bash
mvn -q test
```

`BookControllerFullContextTest` uses `@SpringBootTest` + the real `BookService`.
`BookControllerSliceTest` uses `@WebMvcTest` + a `@MockitoBean BookService`. Watch the timing: the slice test finishes faster because Spring loads only the web layer.
