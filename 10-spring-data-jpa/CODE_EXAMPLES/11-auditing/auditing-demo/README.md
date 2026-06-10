# auditing-demo

`@EnableJpaAuditing` + `@CreatedDate` / `@LastModifiedDate` / `@CreatedBy` / `@LastModifiedBy`. The current-user resolver is a simple `AuditorAware<String>` bean returning `"test-user"`.

Run:

```
mvn test
```
