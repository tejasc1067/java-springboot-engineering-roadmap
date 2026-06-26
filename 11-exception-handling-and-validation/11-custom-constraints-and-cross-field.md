# Custom constraints and cross-field validation — `@Constraint` + `ConstraintValidator`

The built-in annotations (`@NotBlank`, `@Size`, `@Email`, …) cover the common shapes, but they only ever look at *one* value in isolation. Two needs they can't meet: a rule that's specific to your domain (an ISBN-13 has a checksum no `@Pattern` regex can verify), and a rule that spans *two* fields (`endDate` must not precede `startDate`). This topic writes both — a custom **field** constraint and a class-level **cross-field** constraint — and shows that, once written, they flow through the exact same `MethodArgumentNotValidException` and the same `fieldErrors` array as every built-in constraint from topic 08.

---

## The problem this solves

You could try to enforce these with the tools you have, and it falls apart:

```java
// "An ISBN-13 is 13 digits" — a regex gets the shape but NOT the checksum.
@Pattern(regexp = "\\d{13}") String isbn;   // accepts 9780132350885, which is NOT a valid ISBN-13

// "endDate after startDate" — there is no annotation that sees two fields.
@FutureOrPresent LocalDate endDate;          // can't reference startDate at all
```

The regex passes a string whose check digit is wrong, and there is simply no field annotation that can compare `endDate` to `startDate` — a field constraint receives one value and knows nothing about its siblings. Both of these are jobs for a constraint you write yourself.

---

## How it works

A constraint is two pieces: an **annotation** marked `@Constraint`, and a **`ConstraintValidator`** that holds the logic. The annotation is the label you put on a field or class; the validator is the code that runs.

### A custom field constraint: `@ValidIsbn`

The annotation declares three members every constraint must have — `message`, `groups`, `payload` — and points at its validator:

```java
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = IsbnValidator.class)
public @interface ValidIsbn {
    String message() default "must be a valid ISBN-13";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

- `@Constraint(validatedBy = ...)` is what makes this an actual constraint and not just any annotation.
- `message()` is the default violation text (overridable per-use: `@ValidIsbn(message = "...")`). `groups()` lets it participate in validation groups (topic 10). `payload()` is metadata almost nobody uses — but the spec requires all three, so they're boilerplate you copy.
- `@Target({FIELD, PARAMETER})` lets it sit on a record component / field *and* on a method parameter (so it works for the `@Validated` service-parameter case in topic 12). `@Retention(RUNTIME)` is mandatory — the validator is found by reflection at runtime.

The validator carries the logic:

```java
public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;          // null is @NotNull's job, not ours
        if (value.length() != 13) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
            int digit = c - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;   // weights alternate 1,3,1,3,…
        }
        return sum % 10 == 0;                     // the check digit makes the weighted sum divisible by 10
    }
}
```

`ConstraintValidator<ValidIsbn, String>` ties the validator to its annotation (first type) and the type it validates (second). `isValid` returns `true` for pass, `false` for fail — that's the whole contract.

`9780132350884` (Effective Java) passes: `9·1 + 7·3 + 8·1 + 0·3 + 1·1 + 3·3 + 2·1 + 3·3 + 5·1 + 0·3 + 8·1 + 8·3 + 4·1 = 100`, and `100 % 10 == 0`. Flip the last digit to `5` and the sum is 101 — `IsbnValidator` rejects it. A regex never could.

### Why `null` returns `true`

Every built-in constraint follows this convention: `@Size`, `@Email`, `@Pattern` all *pass* on `null`. The reasoning is single responsibility — a constraint validates the **shape of a present value**; whether the value is *required* is a separate rule owned by `@NotNull`/`@NotBlank`. Keeping them apart means a field can be **optional but well-formed**: `@ValidIsbn String isbn` (no `@NotNull`) accepts a missing ISBN but rejects a malformed one. Want it required *and* valid? Stack them: `@NotBlank @ValidIsbn`. If `IsbnValidator` rejected `null` instead, you could never express "optional ISBN," and a missing value would produce two violations.

### A class-level cross-field constraint: `@ValidDateRange`

A field constraint can't compare two fields, so the constraint goes on the **type** (`@Target(TYPE)`) and the validator receives the **whole object**:

```java
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange { /* message, groups, payload */ }

@ValidDateRange
public record LoanPeriod(LocalDate startDate, LocalDate endDate) {}
```

```java
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, LoanPeriod> {
    @Override
    public boolean isValid(LoanPeriod period, ConstraintValidatorContext context) {
        if (period == null || period.startDate() == null || period.endDate() == null) {
            return true;                          // presence is someone else's concern
        }
        if (!period.endDate().isBefore(period.startDate())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endDate")
                .addConstraintViolation();
        return false;
    }
}
```

Because the validator's second type parameter is `LoanPeriod`, `isValid` gets both dates and can compare them. The validated type matches the annotation's target — annotate a `LoanPeriod`, validate a `LoanPeriod`.

---

## Pointing the violation at the right field

A class-level constraint, left alone, reports the violation against the **whole object**: the `fieldErrors` entry would say the field is `loanPeriod`, not which date is wrong. That's useless to a client building a form — it can't highlight the bad input. The `ConstraintValidatorContext` fixes it:

```java
context.disableDefaultConstraintViolation();                       // drop the object-level violation
context.buildConstraintViolationWithTemplate(                      // build a new one…
        context.getDefaultConstraintMessageTemplate())            // …reusing the annotation's message
       .addPropertyNode("endDate")                                 // …attached to the endDate field
       .addConstraintViolation();                                  // …and register it
```

`disableDefaultConstraintViolation()` is essential — without it you get **two** violations (the default object-level one *plus* your `endDate` one). In this demo `LoanPeriod` is nested under `CreateBookRequest`, so the resulting `FieldError` path is `loanPeriod.endDate`, which is exactly what the test asserts. Reusing `getDefaultConstraintMessageTemplate()` keeps the message (`endDate must not be before startDate`) and the override (`addPropertyNode`) in sync; passing a hard-coded string here would silently ignore any per-use `message` override.

## Wiring it into the request

The DTO combines a built-in constraint, the custom field constraint, and a nested object:

```java
public record CreateBookRequest(
        @NotBlank String title,
        @ValidIsbn String isbn,
        @Valid LoanPeriod loanPeriod) {}
```

The `@Valid` on `loanPeriod` is what makes Spring **cascade** into the nested object and run its class-level `@ValidDateRange`. Drop the `@Valid` and the date-range rule silently never fires — the single most common bug with nested validation. The controller is the ordinary `@Valid @RequestBody` from topic 08:

```java
@PostMapping
public ResponseEntity<CreateBookRequest> create(@Valid @RequestBody CreateBookRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(request);
}
```

Both custom constraints fail through `MethodArgumentNotValidException`, so the **same** `handleMethodArgumentNotValid` override from topic 06/08 turns them into the module's `fieldErrors` body — no new handler needed. The test (`CustomConstraintsTest`) proves all three outcomes: a valid ISBN + valid range → 201; check digit `5` → 400 with a `fieldErrors` entry for `isbn`; `endDate` before `startDate` → 400 with a `fieldErrors` entry for `loanPeriod.endDate`.

---

## When to use it / when not to

**Write a custom constraint when** the rule is *reusable* and *declarative*: an ISBN, a strong-password policy, a phone-number format, "end after start." Putting it in an annotation means it's enforced consistently everywhere the DTO is used, and it shows up in the same error contract automatically.

**Don't reach for one when** the rule needs a database or another service — "this ISBN isn't already taken," "this user has permission." Bean Validation runs before your method body and has no clean access to repositories or the security context; a validator that injects a repository couples your input layer to your persistence layer and runs on every request. Those are **business rules**, enforced by a thrown domain exception in the service (topic 07), not a constraint. Topic 12 draws this line explicitly: bean validation checks the *input is well-formed*, the service checks the *operation is allowed*.

---

## Common pitfalls

- **Forgetting `@Retention(RUNTIME)` on the annotation.** The default retention is `CLASS`, so the annotation is dropped before runtime and the validator is never found — your constraint silently does nothing. It must be `RUNTIME`.
- **Forgetting `@Valid` on the nested field.** `@ValidDateRange` is on `LoanPeriod`, but Spring only descends into `loanPeriod` if `CreateBookRequest` marks it `@Valid`. Without it, the cross-field rule never runs and bad ranges sail through as 201.
- **Omitting `disableDefaultConstraintViolation()` when you add a custom property node.** You then get *two* violations — the original object-level one and your field-level one — and the client sees a duplicate, confusing error.
- **Rejecting `null` in the validator.** Breaks the "optional but valid" case and double-reports with `@NotNull`. Return `true` on `null` and let `@NotNull`/`@NotBlank` own presence.
- **Hard-coding the message string in `buildConstraintViolationWithTemplate("…")`.** That ignores any per-use `message` override and any externalized message. Pass `context.getDefaultConstraintMessageTemplate()` so the annotation stays the source of truth.
- **A class-level constraint with no property node, then wondering why the field path is the object name.** That's the default behavior; `addPropertyNode("endDate")` is what moves it onto the field.
- **Putting business rules ("ISBN already exists") in a `ConstraintValidator`.** It needs a repository, runs on every request, and blurs input validation into domain logic. Keep it in the service (topic 07/12).

---

## Try this yourself

The demo project is `CODE_EXAMPLES/11-custom-constraints-and-cross-field/custom-constraints-demo`.

1. Run the assertions, then boot it:
   ```bash
   cd 11-exception-handling-and-validation/CODE_EXAMPLES/11-custom-constraints-and-cross-field/custom-constraints-demo
   mvn -q test
   mvn -q spring-boot:run
   ```
   ```bash
   # valid -> 201
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
        -d '{"title":"Effective Java","isbn":"9780132350884","loanPeriod":{"startDate":"2026-01-01","endDate":"2026-01-15"}}'
   # bad checksum -> 400, fieldErrors has "isbn"
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
        -d '{"title":"X","isbn":"9780132350885","loanPeriod":{"startDate":"2026-01-01","endDate":"2026-01-15"}}'
   # endDate before startDate -> 400, fieldErrors has "loanPeriod.endDate"
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
        -d '{"title":"X","isbn":"9780132350884","loanPeriod":{"startDate":"2026-01-15","endDate":"2026-01-01"}}'
   ```
2. **Break it and watch the test fail:** delete `@Valid` from the `loanPeriod` field in `CreateBookRequest`. Re-run `mvn -q test` — `endDateBeforeStartDateReturns400WithEndDateFieldError` fails, because the cross-field rule no longer cascades and the bad range returns 201. Put it back.
3. In `DateRangeValidator`, comment out `disableDefaultConstraintViolation()`. Re-run the bad-range curl and count the `fieldErrors`: now there are two — one for `loanPeriod` and one for `loanPeriod.endDate`.
4. Add `@NotBlank` in front of `@ValidIsbn` on the `isbn` field, then POST with `"isbn":""`. You get a `@NotBlank` violation, not an ISBN one — confirming the two constraints own different concerns.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`ValidIsbn.java`** — the custom field-constraint annotation: `@Constraint`, `@Target({FIELD,PARAMETER})`, `message`/`groups`/`payload`.
3. **`IsbnValidator.java`** — `ConstraintValidator<ValidIsbn, String>` doing the ISBN-13 checksum; `null` treated as valid.
4. **`ValidDateRange.java`** — the class-level constraint annotation, `@Target(TYPE)`.
5. **`LoanPeriod.java`** — the `record` the cross-field constraint is attached to.
6. **`DateRangeValidator.java`** — compares the two dates and re-attaches the violation to `endDate`.
7. **`CreateBookRequest.java`** — `@NotBlank` + custom `@ValidIsbn` + a `@Valid` nested `LoanPeriod`.
8. **`BookController.java`** — `POST /api/books` with `@Valid @RequestBody`.
9. **`GlobalExceptionHandler.java`** — the module's `handleMethodArgumentNotValid` override; custom constraints reuse it unchanged.
10. **`CustomConstraintsTest.java`** — asserts 201, 400-with-`isbn`, and 400-with-`loanPeriod.endDate`.

## Self-check

1. Why does `IsbnValidator.isValid` return `true` for a `null` input instead of `false`? What would break if it returned `false`?
2. A class-level `@ValidDateRange` fails. By default, what field name shows up in the `fieldErrors` array, and which two `ConstraintValidatorContext` calls move it onto `endDate`?
3. The cross-field rule "stopped working" — bad date ranges now return 201. The annotation and validator are unchanged. What's the one missing annotation, and on which field?
