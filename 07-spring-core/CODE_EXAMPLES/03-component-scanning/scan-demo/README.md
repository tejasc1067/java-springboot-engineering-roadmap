# scan-demo

The topic 02 demo, rewritten so almost all the `@Bean` methods are gone. Each domain class now carries one of the stereotype annotations (`@Service`, `@Component`, `@Repository`); `AppConfig` is reduced to `@Configuration` + `@ComponentScan`.

Run:

```bash
mvn -q compile exec:java
mvn -q test
```

The output is identical to topic 02. The lesson is in the source diff: less typing, same container.

Read in this order: `AppConfig` (now tiny), `OrderService` (notice the `@Service` plus the unannotated constructor — Spring still uses it), then the rest of the services for which stereotype labels you'd pick.
