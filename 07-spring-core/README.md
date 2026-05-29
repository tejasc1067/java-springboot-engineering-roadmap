# 07 — Spring Core

How a Java framework can build your objects for you, hand each one its dependencies, and manage their lifetimes — so you stop writing the wiring by hand.

In module 06, topic 07, you saw why depending on something *given* (a constructor parameter) is easier to test than depending on something *fetched* (a `new` inside a method). The price of that style is bookkeeping: somebody has to create every object and pass each one the things it needs. In a small program that "somebody" is `main`. In a real backend it would be a four-hundred-line wiring file nobody wants to maintain. Spring's **IoC container** is the framework that does that wiring for you — *automated constructor injection* and a few related powers built on top.

This module teaches the container directly: plain Spring Framework, no Spring Boot magic yet. By the end you'll know what a bean is, how it gets created, when it gets destroyed, and how Spring decides which one to give you when there's more than one choice.

## Who this is for

- Anyone who finished module 06 and saw "this is what Spring does for you in module 07" and wants to cash that check.
- Career switchers from Django, Rails, Nest, or Angular — you've used a DI container before; this names the parts in Java.
- Anyone who's read a Spring Boot tutorial and felt like `@Autowired` was magic. It isn't, and this module unmagics it.

## What you'll be able to do at the end

- [ ] Explain what an **IoC container** is and what problem it solves (in your own words, not slogans).
- [ ] Write a `@Configuration` class that declares beans with `@Bean`, bootstrap it with `AnnotationConfigApplicationContext`, and retrieve a bean by type.
- [ ] Use `@ComponentScan` with `@Component`/`@Service`/`@Repository` and say which annotation to pick when.
- [ ] Look at a class with field injection and explain *exactly* why constructor injection is preferred.
- [ ] Resolve a `NoUniqueBeanDefinitionException` two different ways (`@Primary`, `@Qualifier`).
- [ ] Tell the difference between singleton and prototype scopes and recognize the "prototype injected into a singleton" trap.
- [ ] Run code at startup with `@PostConstruct` and at shutdown with `@PreDestroy`.
- [ ] Read a property from `application.properties` into a bean with `@Value`.
- [ ] Define two beans that activate under different `@Profile`s and switch between them at startup.
- [ ] Write a Spring AOP aspect that times every call to a service method.
- [ ] Publish an application event from one bean and handle it in another with `@EventListener`.

If you can do all eleven by the end, the module worked.

## Prerequisites

- Modules 05 and 06 done. You should be able to read a `pom.xml`, run `mvn test`, and recognize what `@Test`, `assertThat`, and `@BeforeEach` do.
- Comfort with module 06 topic 07's central idea: that classes should accept their dependencies, not build them. Spring is what makes that idea practical past five classes.
- A JDK installed (Java 17 or newer; examples target Java 21 — the Spring Framework 6.x baseline).

## How this module is organized

Topic 01 is the motivation: walk the same wiring problem from module 06.07 but with five classes instead of one, then sketch what a container does about it.

Topics 02–03 are how you *register* beans with Spring: explicit `@Bean` methods in a `@Configuration` class, or `@ComponentScan` over `@Component`-annotated classes. Two routes to the same place.

Topics 04–05 are *which* bean goes where: constructor versus setter versus field injection, and what to do when more than one bean matches what's being asked for.

Topics 06–07 are *when* beans exist: singleton vs prototype, and the `@PostConstruct`/`@PreDestroy` hooks that run during the container's own startup and shutdown.

Topics 08–09 are *configuration* — values from outside the code (`@Value`) and environment-specific bean choices (`@Profile`).

Topics 10–11 are the two Spring Core powers most beginners miss: **AOP** (intercepting method calls without touching the called code) and **application events** (one bean publishes, another listens, neither knows the other).

```text
01-why-spring-and-the-ioc-container      <- the wiring-at-scale problem; what an IoC container is
02-application-context-and-beans         <- @Configuration, @Bean, AnnotationConfigApplicationContext
03-component-scanning                    <- @Component, @Service, @Repository, @Controller, @ComponentScan
04-dependency-injection-styles           <- constructor vs setter vs field; why constructor wins
05-qualifier-and-primary                 <- multiple beans of the same type; disambiguation
06-bean-scopes                           <- singleton vs prototype; the prototype-in-singleton trap
07-bean-lifecycle                        <- @PostConstruct, @PreDestroy; init/destroy hooks
08-external-configuration                <- @Value, @PropertySource, defaults, ${...} placeholders
09-profiles                              <- @Profile; dev/prod bean variants
10-spring-aop                            <- @Aspect, @Around; cross-cutting concerns
11-application-events                    <- ApplicationEventPublisher, @EventListener
```

```text
COMMON_MISTAKES.md       <- real Spring-DI bugs with code and the fix
INTERVIEW_QNA.md         <- interview-grade questions on the container, scopes, lifecycle, AOP
```

## The toolkit (what we add to the pom)

Plain Spring Framework only. The four test libraries you already know from module 06 stay for asserting that the wiring did what you expected.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Context | `org.springframework:spring-context:6.2.18` | the IoC container; `@Configuration`, `@Bean`, `@Component`, `ApplicationContext` | topic 02 |
| Spring AOP | `org.springframework:spring-aop:6.2.18` | proxy-based method interception | topic 10 |
| AspectJ Weaver | `org.aspectj:aspectjweaver:1.9.25.1` | the annotation parser Spring AOP delegates to (`@Aspect`, pointcuts) | topic 10 |
| JUnit 5 | `org.junit.jupiter:junit-jupiter:5.10.2` | runs your tests | every topic |
| AssertJ | `org.assertj:assertj-core:3.27.7` | fluent assertions in the tests | every topic |

`spring-context` transitively pulls in `spring-core`, `spring-beans`, `spring-aop` (for proxy support), and `spring-expression` (for `@Value` placeholders). You don't declare those directly.

`spring-aop` *is* already pulled in by `spring-context`, but topic 10 declares it explicitly so the version is visible at the point you start using `@Aspect`. `aspectjweaver` is the only genuinely new dependency in topic 10.

What's **not** here: `spring-boot-*`, `spring-boot-starter-*`, `spring-test`, `MockMvc`, `@SpringBootApplication`, `application.yml`. Module 08 introduces those. Doing them by hand first means you'll know what Spring Boot is doing on your behalf — and what to do when its conveniences get in your way.

## How to run the examples

Each topic is a real Maven project under `CODE_EXAMPLES/NN-topic-name/topic-demo/`. To see a demo's output:

```bash
cd 07-spring-core/CODE_EXAMPLES/02-application-context-and-beans/context-demo
mvn -q compile exec:java          # runs Main; prints what the container did
mvn -q test                       # runs the wiring assertions
```

Or open the topic's project in IntelliJ and click the green ▶ next to `Main` / a `@Test`.

## Checkpoint

You're done with this module when you can:

1. Take a small graph of objects (three or four classes that depend on each other), write the wiring by hand in `main`, then write the same thing with `@Configuration` + `@Bean` and confirm both produce equivalent results.
2. Be shown a class that calls `new SomeService()` inside a method (untestable, from module 06.07) and turn it into a Spring-managed bean wired by constructor injection.
3. Be shown a `NoUniqueBeanDefinitionException` stack trace and choose between `@Primary` and `@Qualifier` to fix it — explaining why one fits better than the other in that case.
4. Write a `@PostConstruct` method that loads something at startup, a `@Profile("dev")` bean that only activates in development, and a timing aspect that logs how long any `@Service` method takes.

If those four feel comfortable, move to module 08, where Spring Boot makes most of this configuration automatic — *because* you've now seen what it's automating.

## A note on what's *not* here

- **No Spring Boot.** No `@SpringBootApplication`, no auto-configuration, no starter dependencies, no `application.yml`. Module 08.
- **No Spring MVC / web layer.** `@Controller` is mentioned as one of the stereotype annotations but no HTTP request handling. Module 09.
- **No Spring Data / JPA.** `@Repository` is mentioned but does not yet talk to a database here. Module 10.
- **No `@SpringJUnitConfig` or `spring-test`.** Tests in this module bootstrap an `AnnotationConfigApplicationContext` directly. The Spring test framework arrives with Spring Boot in module 08.
- **No async or scheduling.** `@Async`, `@Scheduled`, `@EnableAsync` are real Spring Core features but each needs a few paragraphs of careful explanation; they're better introduced alongside Spring Boot's auto-configured task executor.
- **No AspectJ load-time weaving.** Topic 10 uses Spring's *proxy-based* AOP only — the kind you'll actually meet in Spring projects. Full AspectJ is a separate (and rarely needed) topic.
