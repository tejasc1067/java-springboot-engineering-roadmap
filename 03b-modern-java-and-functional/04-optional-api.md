# 04 — Optional API

`Optional<T>` is a wrapper that makes "this value might not exist" part of the type signature instead of a `null` you have to remember to check. Used well, it eliminates a category of `NullPointerException` bugs and makes API contracts honest. Used badly, it adds ceremony and allocations without buying anything.

This topic covers how to create an `Optional`, how to consume it without falling back to `if (x != null)`, and the rules for when to use it.

---

## The problem this solves

```java
User u = repo.findByEmail(email);
String name = u.getName();           // NPE if u is null
```

`findByEmail` might return `null` — and the type signature `User findByEmail(...)` doesn't say so. Every caller has to remember; one forgotten `if (u != null)` and production breaks.

```java
Optional<User> u = repo.findByEmail(email);
String name = u.map(User::getName).orElse("unknown");
```

The type signature tells you the value can be absent. The compiler won't let you call `.getName()` without dealing with that.

---

## Creating an Optional

```java
Optional<String> a = Optional.of("value");           // value must NOT be null. NPE if it is.
Optional<String> b = Optional.ofNullable(maybeNull); // value may be null; safe.
Optional<String> c = Optional.empty();               // no value at all.
```

Rule of thumb:

- You're producing a definite value → `Optional.of(x)`.
- You're wrapping a result that might be null → `Optional.ofNullable(x)`.
- You're returning "not found" → `Optional.empty()`.

---

## Consuming an Optional — the right way

```java
Optional<User> u = repo.findByEmail(email);

// Run a function on the value if present, otherwise return a default.
String name = u.map(User::getName).orElse("unknown");

// Run a side effect only if present.
u.ifPresent(found -> log.info("hit for {}", found.getId()));

// Run different side effects for present/absent (Java 9+).
u.ifPresentOrElse(
    found -> log.info("hit"),
    () -> log.info("miss")
);

// Throw if absent.
User definitely = u.orElseThrow(() -> new NotFoundException(email));

// Lazy default: only build the fallback if needed.
User result = u.orElseGet(() -> buildExpensiveDefault());
```

### `orElse` vs `orElseGet`

```java
u.orElse(buildExpensiveDefault());    // ALWAYS calls buildExpensiveDefault(), even if u is present
u.orElseGet(() -> buildExpensiveDefault());   // only calls when u is empty
```

`orElse` evaluates its argument eagerly — that's the rule for Java method arguments. If the fallback is expensive (DB hit, allocation, computation), use `orElseGet`.

### Don't `isPresent` + `get`

```java
// Anti-pattern: this is `if (u != null) u.x()` in disguise.
if (u.isPresent()) {
    return u.get().getName();
}
return "unknown";

// Idiomatic:
return u.map(User::getName).orElse("unknown");
```

If you're calling `get()`, you're admitting that `Optional` wasn't buying you anything.

---

## Transforming and chaining

`map` and `flatMap` are the same as on `Stream`:

```java
Optional<User> u = repo.findByEmail(email);

// map: User -> String. Result is Optional<String>.
Optional<String> name = u.map(User::getName);

// map again to transform further.
Optional<Integer> nameLength = u.map(User::getName).map(String::length);

// flatMap: when the function ITSELF returns Optional. Otherwise you'd nest.
Optional<Profile> profile = u.flatMap(this::loadProfile);   // loadProfile returns Optional<Profile>
```

If `loadProfile` returned `Optional<Profile>` and you used `map`, you'd get `Optional<Optional<Profile>>`. `flatMap` flattens.

`filter` is also available:

```java
Optional<User> adultUser = repo.findByEmail(email).filter(u -> u.getAge() >= 18);
```

If the predicate is false (or the Optional was empty), the result is empty.

---

## When NOT to use Optional

`Optional` was designed for **return types** that might be absent. That's its lane. Other uses cause more problems than they solve.

| Don't use Optional as... | Why |
|--------------------------|------|
| A **field** of a class | Adds allocation on every read; tools like Jackson/Hibernate don't love it; serialization is awkward |
| A **method parameter** | Callers can still pass `null`; you didn't gain anything |
| A **collection element** | `List<Optional<T>>` is almost always wrong — use `List<T>` and exclude nulls |
| For a **`boolean`** | Just use `boolean`. Three-state booleans are a code smell. |

A class field that's "sometimes there" should be a nullable field with `@Nullable` annotation, or two subclasses, or a sentinel — not `Optional`.

---

## Stream and Optional

`Optional` plays well with streams:

```java
List<String> names = users.stream()
    .map(repo::loadAddress)         // Stream<Optional<Address>>
    .flatMap(Optional::stream)      // unwrap present, drop empty -> Stream<Address>
    .map(Address::getCity)
    .collect(Collectors.toList());
```

`Optional.stream()` (Java 9+) returns a stream with 0 or 1 element — perfect for `flatMap`.

---

## Common pitfalls

- **`Optional.of(x)` with a nullable `x`.** Throws NPE immediately. Use `ofNullable`.
- **`isPresent()` + `get()`.** Defeats the point. Use `map` + `orElse` / `orElseGet`.
- **Expensive fallback in `orElse`.** Always evaluated. Use `orElseGet` with a lambda.
- **Optional fields on JPA entities.** Hibernate doesn't know how to map them sensibly; just use `null` + `@Column(nullable = true)`.
- **Returning `Optional<List<T>>`.** Return an empty list instead. `Optional` represents "no value"; an empty list already does that.
- **Returning `Optional<Collection>` from streams.** Collections are never null; just return the (possibly empty) collection.

---

## Code examples

1. `OptionalCreation.java` — `of`, `ofNullable`, `empty` and what each refuses.
2. `OptionalConsumeBadVsGood.java` — `isPresent + get` vs `map + orElse`.
3. `OrElseVsOrElseGet.java` — measurable cost of building the default eagerly.
4. `OptionalMapAndFlatMap.java` — chained transformations and when to use `flatMap`.
5. `OptionalInStream.java` — `Optional::stream` to flatten and drop empties.

---

## Try this yourself

1. In `OrElseVsOrElseGet.java`, change the fallback function to call a side effect (e.g., `System.out.println`). Notice the print happens with `orElse` even when the Optional is present.
2. In `OptionalCreation.java`, call `Optional.of(null)`. Watch the immediate NPE — it's the whole point.
3. In `OptionalInStream.java`, replace `flatMap(Optional::stream)` with `map(Optional::get)`. Notice when one element is absent the pipeline blows up.

---

## Self-check

1. Why is `Optional.of(maybeNull)` worse than `Optional.ofNullable(maybeNull)` if you don't know whether `maybeNull` is null?
2. `u.orElse(loadExpensiveDefault())` is shorter than `u.orElseGet(this::loadExpensiveDefault)`. Why does the team's style guide still prefer the second?
3. A teammate writes `public void process(Optional<User> u)`. Why is this an anti-pattern, and what's the right signature?
