# 14 — Java 8 Features

Java 8 (released 2014) is when Java caught up with modern languages: lambdas, streams, `Optional`, method references, default methods on interfaces. Every backend codebase you'll touch uses these. If you only learn one set of "modern Java" features, learn these.

This topic isn't trying to be exhaustive — module 03b covers streams in depth. The goal here is for you to recognize and use the basic forms confidently.

---

## Lambda expressions

A lambda is a short way to write a function that's used once. Instead of writing a whole anonymous class, you write the parameter list and the body.

```java
// Old way: anonymous class
Runnable r1 = new Runnable() {
    @Override public void run() {
        System.out.println("hello");
    }
};

// Lambda
Runnable r2 = () -> System.out.println("hello");
```

Both compile to the same thing roughly — but the lambda is a fraction of the code.

The general shape:

```java
(parameters) -> expression
(parameters) -> { statements; }
```

```java
Runnable noArgs        = () -> System.out.println("no args");
Function<Integer,Integer> doubler = n -> n * 2;       // one param, no parens needed
BiFunction<Integer,Integer,Integer> add = (a, b) -> a + b;
Runnable block         = () -> {
    System.out.println("multi");
    System.out.println("line");
};
```

---

## Functional interfaces

A **functional interface** is one with exactly one abstract method. Lambdas are how you implement them concisely. Java's standard library has a bunch of them:

| Interface              | Shape                | Example use         |
|------------------------|----------------------|---------------------|
| `Runnable`             | `() -> void`         | a thread's task     |
| `Supplier<T>`          | `() -> T`            | lazy value generator|
| `Consumer<T>`          | `T -> void`          | `list.forEach(...)` |
| `Function<T,R>`        | `T -> R`             | `stream.map(...)`   |
| `Predicate<T>`         | `T -> boolean`       | `stream.filter(...)`|
| `BiFunction<T,U,R>`    | `(T,U) -> R`         | reducer/combiner    |

You don't have to memorize them all. Notice the pattern: parameters in the name, return type at the end.

The `@FunctionalInterface` annotation marks an interface as intended-for-lambda use. The compiler then errors out if you accidentally add a second abstract method.

```java
@FunctionalInterface
interface Greeter {
    String greet(String name);
}

Greeter formal = name -> "Hello, " + name + ".";
Greeter casual = name -> "hey " + name;
System.out.println(formal.greet("alice"));    // "Hello, alice."
```

---

## Method references

When your lambda just calls an existing method, there's an even shorter form: `Class::method` or `instance::method`.

```java
// Lambda
Function<String,Integer> len1 = s -> s.length();

// Method reference — exactly the same thing
Function<String,Integer> len2 = String::length;

list.forEach(System.out::println);              // same as: list.forEach(s -> System.out.println(s))
```

Four forms:
- `ClassName::staticMethod` → `Integer::parseInt`
- `instance::method` → `System.out::println`
- `ClassName::instanceMethod` → `String::length` (the receiver becomes the first argument)
- `ClassName::new` → `ArrayList::new` (constructor reference)

Use them when they read naturally. Don't force them — if you have to think, write the lambda.

---

## The Stream API

A stream is a pipeline of operations on a sequence of values. You start from a collection (or an array, file, generator), chain transformations, and end with a terminal operation that produces a result.

```java
List<String> names = List.of("alice", "bob", "carol", "dave");

List<String> result = names.stream()
        .filter(n -> n.length() > 3)        // intermediate
        .map(String::toUpperCase)            // intermediate
        .sorted()                            // intermediate
        .toList();                           // terminal — collect into a List

System.out.println(result);     // [ALICE, CAROL]
```

The pattern is always:
1. **Source** — `collection.stream()`, `Stream.of(1, 2, 3)`, `Arrays.stream(arr)`.
2. **Intermediate operations** (return another Stream) — `filter`, `map`, `sorted`, `distinct`, `limit`.
3. **Terminal operation** (produces a result) — `toList()`, `count()`, `findFirst()`, `reduce(...)`, `forEach(...)`.

Streams are lazy: nothing runs until a terminal operation is invoked.

---

## Common stream idioms

```java
// Filter + map + collect
List<Integer> lengths = names.stream()
        .filter(n -> n.startsWith("a"))
        .map(String::length)
        .toList();

// Sum
int total = numbers.stream()
        .mapToInt(Integer::intValue)
        .sum();

// Count
long count = names.stream()
        .filter(n -> n.length() > 3)
        .count();

// Group by
Map<Character, List<String>> byInitial = names.stream()
        .collect(Collectors.groupingBy(n -> n.charAt(0)));
```

Module 03b covers more. The four idioms above will get you 80% of the way.

---

## Optional

`Optional<T>` is a wrapper that may or may not contain a value. The point: a method that *might* not return anything can say so in its return type, instead of returning `null` and hoping the caller checks.

```java
Optional<User> maybeUser = userRepo.findByEmail("alice@example.com");

if (maybeUser.isPresent()) {
    User u = maybeUser.get();
    ...
}

// More idiomatic:
maybeUser.ifPresent(u -> System.out.println(u.name()));

// Or with a default:
User u = maybeUser.orElse(GUEST);
String name = maybeUser.map(User::name).orElse("anonymous");
```

`Optional` is for return types and chaining. Don't use it as a field type, a method parameter, or in a collection — those uses fight the API. The rule: "I might not have a value here" → return `Optional`.

---

## Default methods on interfaces

Java 8 also added default methods to interfaces — interface methods that come with a body. Without this, adding a method to `List` would have broken every existing `List` implementor. With defaults, the JDK could add new methods (like `List.sort` or `Map.getOrDefault`) without breaking the world.

```java
interface Greeter {
    String name();

    default String greet() {
        return "Hello, " + name();
    }
}
```

You'll see this all over the JDK. In your own code, default methods are a useful tool but should be reserved for things that have a sensible default — not as a backdoor for "I didn't want to make a class."

---

## Common pitfalls

- **`Optional.get()` without `isPresent()` check.** Throws `NoSuchElementException`. Use `orElse`, `orElseThrow`, `ifPresent`, or `map`.
- **Streams that do side effects in `map` or `filter`.** Streams are supposed to be functional. Side effects (`list.add(...)` inside a stream) get you race conditions on parallel streams and confused readers on sequential ones.
- **`Optional` as a field or parameter.** It's designed for return values. As a field, it adds an unwrap step everywhere and breaks serialization. Make the field nullable, document it.
- **Lambdas that capture mutable state.** `int counter = 0; list.forEach(x -> counter++);` doesn't compile — captured locals must be effectively final. Use a `Counter` object or a `long[1]` array if you really need it, or restructure into a stream operation.

---

## Code examples

1. `Lambdas.java` — Runnable, Function, Predicate, BiFunction as lambdas, with the equivalent anonymous-class form for one.
2. `MethodReferences.java` — the four kinds of method reference side-by-side with their lambda equivalents.
3. `StreamsBasics.java` — filter/map/sorted/collect, count, sum.
4. `OptionalUsage.java` — `findFirst`, `map`, `orElse`, `ifPresent`. Show the wrong way (`get` without check) and the right way.

---

## Try this yourself

1. In `StreamsBasics.java`, given a `List<Integer>`, write a stream pipeline that returns the sum of squares of the even numbers. Try it both with and without `mapToInt`.
2. In `OptionalUsage.java`, write `Optional<User> findUser(String name)`. Call it from main with both a real and a fake name. Use `.ifPresent` and `.orElse` to handle each case.
3. Replace one for-each loop in `ChooseTheCollection.java` (topic 13) with a stream that does the same thing. Decide which version reads better.

---

## Self-check

1. What's a functional interface, and what's the relationship between a functional interface and a lambda?
2. `Optional.get()` is dangerous — why? What should you call instead?
3. You have `List<String> names`. Write a one-line stream pipeline that returns a `List<String>` containing only the uppercased names longer than 4 characters, sorted.
