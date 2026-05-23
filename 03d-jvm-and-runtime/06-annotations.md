# 06 — Annotations

Annotations are metadata you attach to code — `@Override`, `@Deprecated`, `@Service`, `@Test`. They don't change behavior on their own. They're labels that a compiler, a build tool, or a framework reads via reflection and reacts to. This topic covers the built-in annotations every Java developer should know, how to write your own, and the four rules (retention, target, repeatable, inherited) that determine when and where annotations are visible.

If reflection (topic 05) is *how* frameworks find code, annotations are *what tells them which code matters*. Spring's `@Component`, Hibernate's `@Entity`, JUnit's `@Test`, and Jackson's `@JsonProperty` all work this exact way.

---

## The problem this solves

Before annotations, configuration was external: XML files describing which classes were beans, which methods were tests, which fields mapped to which columns. The configuration drifted from the code; renaming a class silently broke the XML; reviewing what a method did meant opening two files.

Annotations move the metadata to where the code is. Renaming the class breaks the compile (the annotation is on the class). Reviewing what something does requires reading one file. The framework still discovers everything via reflection, but the source of truth is now the code itself.

---

## Built-in annotations

```java
@Override                          // method overrides a superclass / interface method
public String toString() { ... }

@Deprecated(since = "1.4", forRemoval = true)
public void oldApi() { ... }

@SuppressWarnings("unchecked")
List<String> list = (List<String>) raw;

@SafeVarargs                       // promises this varargs method won't pollute the heap with the wrong type
public static <T> void log(T... args) { ... }

@FunctionalInterface               // verifies the interface has exactly one abstract method
public interface Greeter { void greet(); }
```

You'll see `@Override` most often. The other four are situational. None of them affects runtime behavior — they're compiler hints.

---

## Custom annotations — declaring one

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action() default "";
    int priority() default 5;
}
```

Then use it:

```java
@Audit(action = "delete-user", priority = 1)
public void deleteUser(long id) { ... }
```

The framework reads it:

```java
Method m = service.getClass().getDeclaredMethod("deleteUser", long.class);
Audit audit = m.getAnnotation(Audit.class);
log.info("auditing {} (priority {})", audit.action(), audit.priority());
```

Annotations are interfaces (with a special `@interface` declaration). Annotation members look like methods but they are attributes — they can have defaults, and they can only be specific types: primitives, `String`, `Class`, an enum, another annotation, or an array of any of those.

---

## The four meta-annotations

These four control how *your* annotation behaves. They go on the annotation declaration.

### `@Retention` — how long the annotation lives

| Policy | Visible to | Use case |
|--------|-----------|----------|
| `SOURCE` | only the compiler; stripped from `.class` | `@Override`, lint annotations |
| `CLASS` (default) | in the `.class` file; not visible at runtime | rare; bytecode tools |
| `RUNTIME` | available via reflection at runtime | **Almost everything you write.** Spring, JUnit, Jackson all need this. |

If your annotation is going to be inspected by a framework via reflection, you **must** use `RUNTIME`. The default is `CLASS`, which means `m.isAnnotationPresent(YourAnnotation.class)` returns `false` even when the annotation is there. This is the single most common annotation bug.

### `@Target` — where the annotation can be placed

```java
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Audit { ... }
```

| `ElementType` | Where |
|---------------|-------|
| `TYPE` | class, interface, enum, annotation |
| `METHOD` | method |
| `FIELD` | field |
| `PARAMETER` | method parameter |
| `CONSTRUCTOR` | constructor |
| `LOCAL_VARIABLE` | local variable |
| `ANNOTATION_TYPE` | another annotation |
| `PACKAGE` | `package-info.java` |
| `TYPE_PARAMETER` | generic type parameter (`<T @NonNull>`) |
| `TYPE_USE` | any type usage, anywhere (Java 8+) |

Without `@Target`, the annotation can go anywhere (which is rarely what you want).

### `@Repeatable` — allow stacking

```java
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Schedules.class)
public @interface Schedule { String cron(); }

@Retention(RetentionPolicy.RUNTIME)
public @interface Schedules { Schedule[] value(); }

@Schedule(cron = "0 * * * *")
@Schedule(cron = "30 * * * *")
public void job() { ... }
```

Before `@Repeatable` (Java 8), you had to write `@Schedules({...})` manually.

### `@Inherited` — propagate to subclasses

Without `@Inherited`, an annotation on a class is invisible from its subclasses' `getAnnotation(...)`. With it, subclasses see the parent's annotation. `@Inherited` only applies to **class-level** annotations on superclasses (not interfaces, not method-level).

---

## Reflection + annotations

The framework loop, simplified:

```java
for (Method m : clazz.getDeclaredMethods()) {
    Audit a = m.getAnnotation(Audit.class);
    if (a != null) {
        before(a.action());
        try { m.invoke(instance, args); }
        finally { after(a.action()); }
    }
}
```

You can also check parameter annotations (`m.getParameterAnnotations()`) and field annotations (`field.getAnnotation(...)`). This is exactly how Spring builds AOP proxies and how Hibernate maps `@Column`.

---

## Common pitfalls

- **Default retention.** Forgetting `@Retention(RUNTIME)` is the single most common bug. Your framework never finds the annotation.
- **Missing `@Target`.** Without it, callers can put your annotation in surprising places (parameters, local vars). Constrain it.
- **Annotation members that aren't compile-time constants.** Members must be primitives, `String`, `Class`, enums, annotations, or arrays of those. You can't write `Map<String, String>` or arbitrary objects.
- **Expecting annotations to *do* something on their own.** They don't. Something must read them (compiler, framework, your code) and act.
- **Calling `getAnnotations()` instead of `getDeclaredAnnotations()`.** The first walks the inheritance chain *and only for `@Inherited`*. Read the docs; pick deliberately.
- **Annotation-driven magic that's hard to debug.** "Why isn't my `@Transactional` working?" → because the call goes through `this.foo()` instead of a proxied bean. Annotations + framework + proxying is powerful and can hide complexity.

---

## Code examples

1. `BuiltInAnnotations.java` — see `@Override`, `@Deprecated`, `@SuppressWarnings`, `@FunctionalInterface` in action.
2. `CustomAnnotationDeclare.java` — defining an annotation with attributes and reading it via reflection.
3. `RetentionMatters.java` — same annotation, CLASS vs RUNTIME; show the runtime check missing it.
4. `TargetRestriction.java` — annotation refused at the wrong target (compile error explained in code comments + a separate file that demonstrates the runtime lookup).
5. `RepeatableSchedule.java` — stacking the same annotation multiple times.
6. `AnnotationDrivenInvocation.java` — a tiny "AOP" loop: read `@Audit`, wrap calls in before/after logging.

---

## Try this yourself

1. In `RetentionMatters.java`, swap `RUNTIME` for `CLASS` (or remove `@Retention` entirely). Confirm the reflective lookup returns `null`.
2. In `RepeatableSchedule.java`, query both forms: `getAnnotationsByType(Schedule.class)` (flat) and `getAnnotation(Schedules.class)` (container). Compare.
3. In `AnnotationDrivenInvocation.java`, add a parameter to `@Audit` (`boolean trace`) and use it to enable a "trace" branch.

---

## Self-check

1. You wrote `@MyAnnotation` and added it to a method. Your framework's `m.isAnnotationPresent(MyAnnotation.class)` returns `false`. What's the most likely cause?
2. What's the difference between `@Target(METHOD)` and `@Target({METHOD, TYPE})`? What happens if you omit `@Target` entirely?
3. Annotations have member types that look like return types: `String action() default "";`. What set of types are legal for annotation members, and which common Java type is *not* legal?
