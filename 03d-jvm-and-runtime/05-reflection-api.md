# 05 — Reflection API

Reflection lets a running Java program inspect its own classes, fields, methods, and constructors — and call them by name. It's how Spring discovers your `@Service` beans, how Jackson maps JSON fields, how JUnit finds your `@Test` methods, and how Hibernate fills entity objects.

You'll rarely write reflection in application code. You'll use frameworks that do. Understanding reflection is what turns those frameworks from magic into something you can debug.

This topic covers: how to get a `Class<?>`, how to inspect its members, how to invoke them dynamically, the relationship with annotations, and the performance and security caveats.

---

## The problem this solves

Imagine writing a generic "JSON to object" mapper. You don't know at compile time what classes will be passed in. You need to:

1. Look at the target class and find its fields.
2. For each JSON key, find a matching field or setter.
3. Set the value.

There's no way to write that statically — the class isn't known until runtime. Reflection is the API that lets you do it anyway.

The same pattern shows up everywhere:

- DI: "instantiate every class annotated `@Component`."
- ORM: "for every `@Entity`, map columns to fields."
- Test runner: "find every method annotated `@Test`, call it."

---

## Getting a `Class<?>`

Three ways:

```java
Class<?> a = String.class;                  // compile-time literal (preferred)
Class<?> b = "hello".getClass();             // from any object
Class<?> c = Class.forName("java.lang.String");  // from a fully-qualified name string
```

Difference between `.class` and `Class.forName(...)`:

- `.class` is a compile-time constant. No class loading happens.
- `getClass()` returns the **runtime type** of the receiver, which may be a subclass.
- `Class.forName(name)` loads and **initializes** the class (runs `<clinit>`) unless you use the 3-arg overload `Class.forName(name, false, loader)`.

---

## Inspecting members

```java
Class<?> c = Customer.class;

// Fields
Field[] all      = c.getDeclaredFields();   // includes private; this class only
Field[] inherited = c.getFields();          // public only; includes inherited

// Methods
Method[] methods = c.getDeclaredMethods();   // includes private; this class only

// Constructors
Constructor<?>[] ctors = c.getDeclaredConstructors();

// Name, modifiers
String name = c.getName();
int mods = c.getModifiers();
boolean isPublic = Modifier.isPublic(mods);

// Parents and interfaces
Class<?> parent = c.getSuperclass();
Class<?>[] interfaces = c.getInterfaces();
```

`getDeclared...` returns *this class's* members regardless of visibility.
`get...` (without "Declared") returns *public* members including inherited.

---

## Reading and writing fields

```java
Customer cust = new Customer("Alice", 25);
Field nameField = Customer.class.getDeclaredField("name");
nameField.setAccessible(true);              // bypass private
String value = (String) nameField.get(cust);
nameField.set(cust, "Bob");
```

`setAccessible(true)` is what breaks encapsulation. Since Java 9, the module system can refuse access to JDK internals (`InaccessibleObjectException`). For your own code it works as before.

---

## Invoking methods

```java
Method m = Calculator.class.getDeclaredMethod("add", int.class, int.class);
m.setAccessible(true);
int result = (int) m.invoke(new Calculator(), 3, 4);     // returns 7
```

`invoke(target, args...)`:

- `target` is the instance (or `null` for `static`).
- args are the method parameters.
- The return value comes back as `Object` — you cast it.

Exceptions thrown by the invoked method are wrapped in `InvocationTargetException`; the real cause is `e.getCause()`.

---

## Creating instances

```java
Customer c1 = Customer.class.getDeclaredConstructor().newInstance();    // no-arg
Constructor<Customer> ctor = Customer.class.getDeclaredConstructor(String.class, int.class);
Customer c2 = ctor.newInstance("Alice", 25);
```

`Class.newInstance()` (without the constructor lookup) exists but is deprecated — it can't handle constructors that throw checked exceptions cleanly. Use `getDeclaredConstructor().newInstance()`.

---

## Reflection + annotations — how frameworks work

The combination is what makes Spring, JUnit, Jackson, etc. tick:

```java
for (Method m : clazz.getDeclaredMethods()) {
    if (m.isAnnotationPresent(Test.class)) {     // JUnit-style
        m.invoke(instance);
    }
}
```

The framework:

1. Scans all loaded classes for those with relevant annotations.
2. Reads annotation attributes (`@RequestMapping("/users")`).
3. Builds metadata.
4. Invokes methods reflectively when the application needs them.

Annotation retention has to be `RUNTIME` for this to work (topic 06).

---

## Performance — reflection is slower

Every reflective call involves:

- Method-handle lookup.
- Security checks.
- Argument boxing (primitives become `Integer`, `Long`, etc.).
- No JIT inlining of the target (until inline caches stabilize).

A direct method call is on the order of nanoseconds. A reflective one is on the order of hundreds of nanoseconds. Frameworks mitigate by:

1. Caching `Method` and `Field` objects (don't `getDeclaredMethod` per call).
2. Using `MethodHandle` (lower overhead than `Method`).
3. Generating bytecode at startup (Spring's `cglib` proxies, Hibernate's `bytecode-enhanced` entities) so the reflective work happens once.

For your own code: don't reflect on hot paths. Cache lookups.

---

## Security and module visibility

`setAccessible(true)` lets you read private fields. That's powerful and dangerous:

- It breaks encapsulation. Refactors silently break tests written this way.
- In Java 9+, accessing JDK internals throws `InaccessibleObjectException` unless the target module exports the package or you pass `--add-opens` on the command line.
- A `SecurityManager` (deprecated but still configurable) can refuse `setAccessible`.

Use reflection for the cases that need it — frameworks, generic serializers, testing — not as a shortcut around `public` API design.

---

## Common pitfalls

- **Forgetting `setAccessible(true)` on private members.** `IllegalAccessException`.
- **Looking up `Method` per call.** Cache it. A `HashMap<String, Method>` keyed by name avoids the cost.
- **Catching `InvocationTargetException` and looking at the wrong cause.** Unwrap with `.getCause()`.
- **Confusing `getMethod` and `getDeclaredMethod`.** The first walks the hierarchy and is public-only; the second is this class only and includes private.
- **Reflection on records or sealed types.** Records have a fixed shape; reflection works but the record contract may complicate downstream code.
- **Reflective field write after the class is JIT-compiled with the old value baked in.** Constants (`static final` primitives or String) may be inlined; writing them via reflection has no observable effect.

---

## Code examples

1. `InspectClass.java` — print fields, methods, constructors, modifiers, parent class.
2. `ReadPrivateField.java` — read a private field through reflection.
3. `InvokeMethodDynamically.java` — call a method by name and capture the return value.
4. `CreateInstanceReflectively.java` — call a constructor by parameter types.
5. `TinyTestRunner.java` — minimal JUnit-style: scan for methods annotated `@Test`, invoke each.
6. `ReflectionPerformance.java` — direct call vs reflective call vs cached reflective call.

---

## Try this yourself

1. In `ReflectionPerformance.java`, vary the iteration count. Reflective calls scale linearly; the direct call is JIT-optimized into nothing.
2. In `TinyTestRunner.java`, add a method annotated `@Test` that throws. Make the runner print the cause from `InvocationTargetException.getCause()`.
3. In `InvokeMethodDynamically.java`, call a method that returns `void`. Confirm `invoke` returns `null`.

---

## Self-check

1. Your colleague writes `Class.forName("com.example.Foo")` in a `static` field initializer. Will that trigger `Foo`'s static block? What about `Foo.class`?
2. The framework reads `@Service` annotations at startup and builds a registry of beans. Which annotation retention policy must `@Service` use, and why?
3. You have a method called `addUser(User u)` in a class. Sketch the reflective call to invoke it on an instance with a `User` you construct.
