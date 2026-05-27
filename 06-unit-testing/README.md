# 06 — Unit Testing

How to write code that proves itself correct — automatically, every time you build — instead of eyeballing `System.out.println` output and hoping.

Up to now you've checked your code by running a `main` method and reading what it printed. That works for one example. It does not work when you have fifty classes and a change in one quietly breaks another. A unit test is a small piece of code that calls your code with known inputs and *fails the build* if the output is wrong. This module teaches you to write them with the exact toolkit a real Spring Boot project uses.

By the end you'll be able to write a test, run it inside a Maven build, mock a dependency, and read a coverage report — and know which of those actually tell you something.

## Who this is for

- Anyone who finished module 05 and can read a `pom.xml`. We add four test dependencies to one and go from there.
- Career switchers who've used `pytest`, `jest`, or `RSpec` and want the Java vocabulary (JUnit, AssertJ, Mockito).
- Anyone who's written code but never written a test, and isn't sure what's even worth testing.

## What you'll be able to do at the end

- [ ] Explain what a *unit* test is and how it differs from an integration or end-to-end test.
- [ ] Add JUnit 5 to a Maven project and write a test that runs on `mvn test`.
- [ ] Choose between JUnit's built-in assertions and AssertJ's fluent ones, and read both.
- [ ] Use `@BeforeEach`/`@AfterEach` correctly and explain why each test gets a fresh instance.
- [ ] Test the unhappy path: assert that the right exception is thrown for bad input.
- [ ] Collapse twenty near-identical tests into one `@ParameterizedTest`.
- [ ] Recognize *untestable* code (a `new` or a `LocalDateTime.now()` buried in a method) and refactor it so a test can control it.
- [ ] Replace a real dependency with a Mockito mock, stub its return value, and verify it was called.
- [ ] Write a test test-first (red → green → refactor).
- [ ] Generate a JaCoCo coverage report and explain why 100% coverage is not the goal.

If you can do all ten at the end, the module worked.

## Prerequisites

- Module 05 done. You should be able to read a `pom.xml` and know what `<scope>test</scope>` means — every test library here uses it.
- A JDK installed (Java 17 or newer; examples target Java 21, the Spring Boot 3.x LTS standard).
- Comfort running `mvn test` from a terminal (or your IDE's green "run test" arrow).

## How this module is organized

Topics 01–02 set up *why* and *how to run one test*. No mocking yet.

Topics 03–06 are the everyday mechanics: assertions, the test lifecycle, testing failures, and parameterized tests.

Topic 07 is the hinge of the whole module: **why some code is easy to test and some is impossible**, and the one refactor (constructor injection) that fixes it. It's also why Spring's dependency injection exists — you'll meet that in module 07.

Topics 08–09 are test doubles with Mockito: faking the dependencies your code talks to.

Topics 10–12 are judgment: what makes a test *good*, how to work test-first, and what a coverage number does and doesn't mean.

```text
01-why-unit-tests-and-the-test-pyramid   <- the println-and-hope problem; unit vs integration vs e2e
02-junit5-setup-and-first-test           <- add JUnit to a pom, write @Test, run mvn test
03-assertions                            <- JUnit assertions, then AssertJ fluent assertions
04-test-lifecycle                        <- @BeforeEach/@AfterEach, fresh instance per test
05-testing-exceptions-and-edge-cases     <- assertThrows, boundaries, null/empty
06-parameterized-tests                   <- one test, many inputs
07-designing-for-testability             <- why `new` inside a method makes code untestable
08-test-doubles-and-mockito              <- mock / stub / spy / fake; @Mock, when(), verify()
09-mockito-in-depth                      <- matchers, verify counts, ArgumentCaptor, spies
10-what-makes-a-good-test                <- naming, Arrange-Act-Assert, independence, determinism
11-tdd-red-green-refactor                <- write the failing test first
12-code-coverage-with-jacoco             <- measuring coverage, and the 100% trap
```

```text
COMMON_MISTAKES.md       <- real testing bugs with code and fix
INTERVIEW_QNA.md         <- interview-grade questions with deep answers
```

## The toolkit (what we add to the pom)

Every code example is a small real Maven project. Four test-scoped dependencies do all the work — the same four that `spring-boot-starter-test` will bundle for you in module 08:

| Library | What it does | Introduced in |
|---------|--------------|---------------|
| JUnit 5 (`junit-jupiter`) | finds and runs your tests; `@Test`, `@BeforeEach`, assertions | topic 02 |
| AssertJ (`assertj-core`) | fluent, readable assertions: `assertThat(x).isEqualTo(...)` | topic 03 |
| Mockito (`mockito-junit-jupiter`) | fake dependencies so you can test a class in isolation | topic 08 |
| JaCoCo (`jacoco-maven-plugin`) | measures which lines/branches your tests actually exercised | topic 12 |

You don't need all four from the start. Each topic's `pom.xml` adds only what that topic uses.

## Checkpoint

You're done with this module when you can:

1. Take a plain Java class with some logic and write a test that fails when the logic is wrong.
2. Look at a method that calls `new SomeDependency()` inside itself and say "that's why this is hard to test" — then refactor it to take the dependency in its constructor.
3. Write a test that mocks a repository, stubs one method, and verifies another was called exactly once.
4. Run `mvn test`, get a JaCoCo report, and explain why a class at 100% coverage can still be broken.

If those four feel comfortable, move to module 07 (Spring Core), where the dependency injection you did by hand in topic 07 becomes something the framework does for you.

## A note on what's *not* here

- **No Spring testing.** `@SpringBootTest`, `MockMvc`, `@DataJpaTest`, `@MockBean` all live in module 08. Everything here is plain Java so you learn the testing ideas without also learning a framework.
- **No integration tests.** Tests here never touch a real database, network, or filesystem. Talking to a real database in a test is a different skill (and module).
- **No Testcontainers, no CI.** Running tests automatically on every push belongs in module 14.
- **No mutation testing or advanced Mockito** (mocking statics, `BDDMockito`). Mentioned once, not taught — you can read them later.
