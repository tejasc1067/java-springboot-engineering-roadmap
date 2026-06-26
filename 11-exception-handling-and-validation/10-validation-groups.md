# Validation groups — one DTO, different rules for create vs update

Topics 08 and 09 ran *every* constraint on a DTO, every time. But the same payload often has different rules depending on the operation: on **create**, the client must not send an `id` (the server assigns it); on **update**, the `id` is required (which row are you changing?). Validation groups let one DTO carry both rule sets and pick the right one per request — without a second DTO class and without an `if` in the controller.

---

## The problem this solves

The textbook fix is two DTOs: `CreateBookRequest` (no `id`) and `UpdateBookRequest` (`id` required). That works, but the two classes are 90% identical — same `title`, same future fields — and they drift: someone adds `@Size(max = 200)` to `title` on one and forgets the other. You wanted "same shape, one rule differs," and you got two files to keep in sync.

Validation groups express exactly that: **one** `BookPayload`, with the `id` constraint tagged so it only fires on the operation that cares.

```java
public record BookPayload(
        @Null(groups = OnCreate.class,  message = "id must be absent on create")
        @NotNull(groups = OnUpdate.class, message = "id is required on update")
        Long id,

        @NotBlank
        String title) {}
```

`@Null` and `@NotNull` are mutually exclusive on the same field — but they never run together, because each is scoped to a different group.

---

## How it works

A **group** is just an empty marker interface. The demo has two:

```java
public interface OnCreate {}
public interface OnUpdate {}
```

A constraint's `groups` attribute lists the groups it belongs to. When you trigger validation *for a group*, only the constraints tagged with that group run. The trigger is the key annotation: **`@Valid` cannot name a group** — it always runs the default set. To choose a group you need Spring's **`@Validated`** (from `org.springframework.validation.annotation`, not `jakarta.validation`), which takes the group classes as its value:

```java
@PostMapping
public ResponseEntity<BookPayload> create(
        @Validated({Default.class, OnCreate.class}) @RequestBody BookPayload payload) { ... }

@PutMapping("/{id}")
public BookPayload update(
        @PathVariable Long id,
        @Validated({Default.class, OnUpdate.class}) @RequestBody BookPayload payload) { ... }
```

`BookController.create` requests `OnCreate`, so the `@Null` on `id` fires and a client-supplied `id` is rejected. `BookController.update` requests `OnUpdate`, so the `@NotNull` fires and a missing `id` is rejected. Same DTO, opposite verdicts. The `ValidationGroupsTest` proves all four corners: POST with an id → 400, POST without → 201, PUT without an id → 400, PUT with one → 200.

A `@Validated @RequestBody` that fails still throws `MethodArgumentNotValidException` — groups change *which* constraints run, not the *type* of failure — so the same `handleMethodArgumentNotValid` override from topic 06 handles it and the response carries the familiar `fieldErrors` array naming `id`.

---

## The default-group subtlety — the bug everyone hits once

Notice the `@NotBlank` on `title` has **no `groups` attribute**. A constraint with no group implicitly belongs to one group: `jakarta.validation.groups.Default`. This is the trap:

> When you trigger validation for *only* a custom group, the `Default` group is **not** included. Constraints with no `groups` attribute silently do not run.

So if the create handler said `@Validated(OnCreate.class)` — just the custom group — the `@Null` on `id` would run, but the `@NotBlank` on `title` would **not**. A request with a blank title would sail through as a 201. The code compiles, the create-rejection test still passes, and the hole is invisible until a client posts garbage.

There are two correct fixes:

1. **Request `Default` alongside the custom group** (what the demo does): `@Validated({Default.class, OnCreate.class})`. Now `title` (Default) and `id` (OnCreate) both run.
2. **Tag the shared constraints into every group too**: `@NotBlank(groups = {OnCreate.class, OnUpdate.class}) String title`. More verbose, and you must remember it on each new field.

Option 1 is the idiom: list `Default.class` first, then the operation group. Reach for option 2 only when you have a constraint that should run on *some* custom groups but never the default.

---

## When to use it / when not to

**Use groups when** one DTO genuinely has the same shape across operations and only a constraint or two differs (the create-vs-update `id` case is the canonical one). You save a class and the rules can't drift apart.

**Skip groups when** the create and update payloads actually differ in *fields*, not just constraints — if update accepts `status` but create doesn't, two records are clearer than one record with half its fields nullable. Groups manage *which constraints run*, not *which fields exist*. Don't twist a DTO into one shape just to avoid a second class.

---

## Common pitfalls

- **Using `@Valid` and expecting it to honor a group.** `@Valid` has no group attribute; it runs the Default group only. You must use `@Validated(...)` to select a group. This is the single most common mistake — the group annotation is on the DTO, looks wired up, and quietly never fires.
- **Naming only the custom group and losing the default constraints.** `@Validated(OnCreate.class)` drops every un-grouped constraint (like `@NotBlank title`). Use `@Validated({Default.class, OnCreate.class})`.
- **Putting `@Validated` on the *class* and expecting it to pick a group per method.** Class-level `@Validated` (topic 09) is for `@PathVariable`/`@RequestParam` and takes no per-request group. For body groups, put `@Validated(group)` on the *parameter*.
- **`@Null` and `@NotNull` on the same field with no groups.** Without groups they're in the same Default set and contradict each other — every value fails one of them. Groups are what make the pair legal: each runs in a different operation.
- **Forgetting the group on a *new* shared field.** If you chose option 2 (tagging shared constraints into each group) and later add `@Size String author` without groups, it falls back to Default and won't run under `@Validated(OnCreate.class)`. Option 1 (always include `Default.class`) sidesteps this.
- **Group inheritance surprises.** A group interface can `extends` another; validating the child group also runs the parent's constraints. Handy for an `OnUpdate extends OnCreate` hierarchy, but easy to trigger constraints you didn't expect — keep the hierarchy flat unless you need it.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/10-validation-groups/validation-groups-demo`.

1. `cd 11-exception-handling-and-validation/CODE_EXAMPLES/10-validation-groups/validation-groups-demo`, then `mvn -q spring-boot:run` and run the four curls from the README. Watch the same DTO accept a body on one verb and reject it on the other.
2. Look at the 400 bodies: `fieldErrors[0].field` is `id`, with the group-specific message (`id must be absent on create` vs `id is required on update`).
3. **Break it and watch the test miss it.** In `BookController.create`, change `@Validated({Default.class, OnCreate.class})` to `@Validated(OnCreate.class)`. Run `mvn -q test` — all four still pass. Now add a fifth assertion: POST `{"title":""}` and expect 400. It fails, because a blank `title` (a Default constraint) no longer runs. Restore `Default.class` and the new assertion passes. That's the default-group subtlety, felt.
4. Swap the PUT handler's group to `@Validated({Default.class, OnCreate.class})` (the wrong group). Re-run — `updateWithoutIdIsRejected` now fails, because `OnCreate` makes `id` forbidden, not required. The group is the only thing that flipped the rule.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`OnCreate.java`** — an empty marker interface; one validation group.
3. **`OnUpdate.java`** — the sibling group for the update path.
4. **`BookPayload.java`** — one DTO; `id` is `@Null` under `OnCreate` and `@NotNull` under `OnUpdate`, `title` is an un-grouped (Default) `@NotBlank`.
5. **`BookController.java`** — POST uses `@Validated({Default.class, OnCreate.class})`, PUT uses `@Validated({Default.class, OnUpdate.class})`; shows `@Valid` can't do this.
6. **`GlobalExceptionHandler.java`** — the topic-06 `handleMethodArgumentNotValid` override, adding the `fieldErrors` array.
7. **`ValidationGroupsTest.java`** — all four corners: POST with/without id, PUT with/without id, asserting `fieldErrors` names `id`.

## Self-check

1. Why does `@Validated(OnCreate.class)` let a blank `title` through, and what's the one-word fix in the annotation?
2. You have `@Valid @RequestBody BookPayload` and the `id` field is `@Null(groups = OnCreate.class)`. A POST with an id arrives. Is it rejected? Why or why not?
3. `@Null` and `@NotNull` are on the same `id` field. Why doesn't every single request fail validation?
