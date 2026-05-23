# 03 — Generics

Generics let one piece of code work with many types while keeping type safety. `List<String>` and `List<Integer>` share the same `ArrayList` implementation, but the compiler stops you from mixing them up. Before generics (pre-Java 5), collections held `Object`, so you had to cast every element and any cast could blow up at runtime.

This topic covers the everyday side: generic classes, generic methods, the diamond operator, why generics matter for type safety. Wildcards, bounds, and PECS are in [topic 04](04-generics-advanced.md).

---

## The problem this solves

Without generics, every collection holds `Object`:

```java
// Pre-Java 5 style — still legal but dangerous
List list = new ArrayList();
list.add("hello");
list.add(42);                          // no compile error

String s = (String) list.get(1);       // ClassCastException at runtime
```

The bug: `list.get(1)` returns `Object`. You cast to `String`. It explodes. The compiler couldn't help because the list was untyped.

With generics:

```java
List<String> list = new ArrayList<>();
list.add("hello");
list.add(42);                          // ← compile error, caught immediately
```

Type errors caught at compile time, not at 3 AM in production.

---

## Generic classes

You write `<T>` (or any name — `T`, `E`, `K`, `V` by convention) as a placeholder. The compiler stamps out a type-checked version for each use.

```java
public class Box<T> {
    private T value;
    public void set(T value) { this.value = value; }
    public T get()           { return value; }
}

Box<String> sb = new Box<>();
sb.set("hello");
String s = sb.get();                   // no cast needed

Box<Integer> ib = new Box<>();
ib.set(42);
// sb.set(42);                         // compile error — type-safe
```

The `<>` on the right is the **diamond operator** (Java 7+). The compiler infers `<String>` from the left-hand side.

Convention for parameter names:
- `T` — type
- `E` — element (for collections)
- `K`, `V` — key, value (for maps)
- `R` — return type
- `T1`, `T2`, ... if you need multiple unrelated types

---

## Generic methods

A method can have its own type parameter, independent of any class type parameters. Declared before the return type.

```java
public static <T> T firstOrNull(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
}

String s = firstOrNull(List.of("a", "b"));      // T inferred as String
Integer i = firstOrNull(List.of(1, 2, 3));      // T inferred as Integer
```

The `<T>` before the return type declares the parameter. It's scoped just to that method.

Useful when:
- The method works with a type that's unrelated to the class's own generics.
- You want to express "input and output share a type" without committing the whole class.

---

## Multiple type parameters

```java
public class Pair<K, V> {
    private final K key;
    private final V value;
    public Pair(K key, V value) { this.key = key; this.value = value; }
    public K getKey()   { return key; }
    public V getValue() { return value; }
}

Pair<String, Integer> p = new Pair<>("age", 30);
```

Standard library examples: `Map<K, V>`, `BiFunction<T, U, R>`, `Function<T, R>`.

---

## Generic interfaces

```java
public interface Repository<T, ID> {
    T findById(ID id);
    void save(T entity);
}

public class UserRepository implements Repository<User, Long> {
    public User findById(Long id)   { ... }
    public void save(User entity)   { ... }
}
```

A single interface declares the contract; each implementation fixes the type parameters. This is exactly how Spring Data's `JpaRepository<T, ID>` works.

---

## Type erasure: what the JVM actually sees

This is the surprising part. **At runtime, the type parameters are gone.** The JVM sees `List` everywhere, not `List<String>` or `List<Integer>`. Java does this for backward compatibility with code from before generics existed.

Consequences:

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass());   // true — both are ArrayList.class

if (someList instanceof List<String>) { ... }       // ← won't compile (mostly; raw List<?> works)

T value = new T();                                  // ← won't compile — no type info at runtime
```

The compiler erases `<T>` and inserts casts where needed. From the JVM's perspective, a `List<String>` is just a `List`. This shows up in topic 04 with wildcards, and again later with reflection.

Practical implication: don't write code that needs the actual generic type at runtime. If you do (e.g. JSON deserialization frameworks), you'll need a `Class<T>` token or `TypeReference`-style hack.

---

## The raw type trap

You can still write `List list = new ArrayList();` without generics — a "raw type." The compiler will warn, but it'll compile. Don't do this in new code. Once you mix raw and parameterized types, type safety collapses and you get unchecked warnings everywhere.

```java
List raw = new ArrayList<String>();
raw.add(42);                                        // compiles, corrupts the list
List<String> typed = raw;                           // now typed has an Integer in it
String s = typed.get(0);                            // ClassCastException
```

Always parameterize. If you mean "any type," use `List<Object>` or `List<?>` (covered in topic 04), not raw `List`.

---

## Common pitfalls

- **Forgetting the diamond on the right.** `List<String> list = new ArrayList();` compiles with a raw-type warning. Always `new ArrayList<>()`.
- **Trying to `new T()` inside a generic class.** Erasure means there's no `T` at runtime. Pass a `Class<T>` if you need to instantiate, or accept a `Supplier<T>`.
- **Generic arrays.** `T[] arr = new T[10];` won't compile. The standard workaround is `@SuppressWarnings("unchecked") T[] arr = (T[]) new Object[10];` — but using `ArrayList<T>` is almost always cleaner.
- **Casting between parameterized types.** `List<Object> != List<String>`. Even though `Object` is a supertype of `String`, the lists are not assignable. Topic 04 (wildcards) is the right tool for this.
- **`Object` as a substitute for generics.** `List<Object>` accepts everything but loses all type info. Pick the actual type or use wildcards.

---

## Code examples

1. `RawTypeDanger.java` paired with `GenericsTypeSafe.java` — the pre-Java 5 bug vs. the generic fix.
2. `BoxGenericClass.java` — a basic `Box<T>` used with multiple types.
3. `GenericMethod.java` — a generic method (`<T> T firstOrNull`) used independently of any class generics.
4. `PairTwoParams.java` — multiple type parameters (`<K, V>`).
5. `GenericRepositoryInterface.java` — generic interface with implementations fixing the parameters.
6. `TypeErasureProof.java` — `List<String>.class == List<Integer>.class` and what that means.

---

## Try this yourself

1. In `RawTypeDanger.java`, add a print after every `add` and `get`. Trace which line actually fails. (It's not the line you'd expect.)
2. In `GenericMethod.java`, call `firstOrNull` with a `List<Object>` containing mixed types. Now narrow the parameter to `List<String>`. Notice how the type system tightens automatically.
3. In `TypeErasureProof.java`, try to add an `instanceof List<String>` check. Read the compile error and rephrase it using `List<?>` instead — note what information you lose.

---

## Self-check

1. Why is `List<Object>` not a supertype of `List<String>`? (Hint: think about what `list.add(new Object())` would do.)
2. The JVM doesn't know the generic type at runtime. Give one concrete consequence of this in code you might write.
3. You want a method that takes a list of any type and returns the first element. Write the signature. Why does the type parameter go before the return type?
