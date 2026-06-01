# starters-demo

One `pom.xml` with two starters and zero version pins. The test class uses JUnit Jupiter, AssertJ, and Mockito together — all three came from `spring-boot-starter-test`.

## Run

```bash
mvn -q test                                                    # passes
mvn -q dependency:tree                                         # what the two starters brought in
mvn -q dependency:tree | grep -E "(mockito|assertj|junit)"     # the three libs the test exercises
```

## What to notice

- `pom.xml` lists two `<dependency>` blocks. Neither has a `<version>`. The `spring-boot-starter-parent` parent POM pins them — and pins every transitive too.
- `RetryPolicyTest` uses three test libraries in one file (`@Test`, `assertThat`, `mock`). All three are on the classpath because `spring-boot-starter-test` is a curated bundle, not a single library.
- `RetryPolicy` itself is a plain Java class — not a Spring bean — to keep the lesson on starters rather than DI.
