# relationship-demo

An `Author` and a `Book` linked by a `@OneToMany` / `@ManyToOne` pair.

Run:

```
mvn test
```

Watch the console for the `author_id` foreign-key column Hibernate generates on the `books` table.
