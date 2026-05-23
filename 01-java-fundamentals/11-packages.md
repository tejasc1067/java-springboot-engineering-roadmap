# 11 — Packages

A package is a folder full of related classes, with a name. That's all it is at the file-system level. What makes packages useful: they give every class a fully-qualified name (so two unrelated `User` classes can coexist), they let access modifiers do their job (`package-private` means "this package only"), and they're the unit you organize code into as a project grows.

This topic is mostly about conventions and the mechanics of `package` and `import`. The runnable examples in this module use the default (unnamed) package because they're single-file launches. Real projects will look like the example layout below.

---

## What a package looks like

```text
src/
└── com/
    └── acme/
        └── shop/
            ├── Order.java
            ├── Customer.java
            └── pricing/
                ├── Discount.java
                └── TaxCalculator.java
```

Each `.java` file declares its package on the first line:

```java
// In src/com/acme/shop/Order.java
package com.acme.shop;

public class Order { ... }
```

```java
// In src/com/acme/shop/pricing/TaxCalculator.java
package com.acme.shop.pricing;

public class TaxCalculator { ... }
```

The package name **must match the folder structure** under the source root. The compiler enforces it: `Order.java` in `src/com/acme/shop/` must say `package com.acme.shop;`.

---

## Fully-qualified names

A class's full name is `package.ClassName`:

```text
com.acme.shop.Order
com.acme.shop.pricing.TaxCalculator
java.util.ArrayList
java.time.LocalDate
```

You can always refer to a class by its full name:

```java
java.util.ArrayList<String> names = new java.util.ArrayList<>();
```

That's verbose. The `import` statement lets you use the short name:

```java
import java.util.ArrayList;

ArrayList<String> names = new ArrayList<>();
```

The import doesn't "load" anything at runtime. It only tells the compiler, "when I write `ArrayList`, I mean `java.util.ArrayList`." Imports are a source-file convenience.

---

## import: the rules

**Named import** — bring one class into scope.

```java
import java.util.ArrayList;
import java.util.List;
```

**Wildcard import** — bring an entire package's classes into scope.

```java
import java.util.*;     // brings ArrayList, HashMap, List, Map, ...
```

Style-wise, named imports are clearer. Most teams configure their IDE to use named imports automatically.

**Static import** — bring a class's static members into scope, so you can call them without the class name.

```java
import static java.lang.Math.PI;
import static java.lang.Math.sqrt;

double area = PI * radius * radius;
double s = sqrt(2);
```

Use static imports sparingly. They make code shorter but harder to read, because `sqrt(x)` doesn't tell you where `sqrt` came from. Common exception: test-framework assertions (`import static org.junit.jupiter.api.Assertions.*`).

**No import needed:**
- Classes in `java.lang` (`String`, `Object`, `Integer`, `Math`, `Thread`, ...) — auto-imported.
- Classes in the same package.

---

## Package naming convention

The standard is **reverse-domain-name + project + module**:

```text
com.acme.shop                  ← company.product
com.acme.shop.pricing          ← subsystem
io.spring.framework            ← open source
org.apache.commons.lang3       ← open source
```

If you don't own a domain, anything unique works (`net.tejas.learn`). What you should avoid:

- Single-name packages (`shop`) — collide with everyone.
- Uppercase letters in package names — convention is all lowercase.
- Java reserved words anywhere in the path.

---

## Why packages matter for access modifiers

The default access level (no keyword) is called **package-private**. A class or member with default access is visible only inside the same package. This is how you give a few related classes shared access without exposing them to the world.

```java
// File: com/acme/shop/Order.java
package com.acme.shop;

public class Order {
    int subtotal;       // package-private — visible to other classes in com.acme.shop
}

// File: com/acme/shop/Customer.java
package com.acme.shop;

class Customer {
    void example(Order o) {
        System.out.println(o.subtotal);    // works — same package
    }
}

// File: com/other/Outside.java
package com.other;

import com.acme.shop.Order;

class Outside {
    void example(Order o) {
        System.out.println(o.subtotal);    // compile error — package-private
    }
}
```

This is heavily used in framework code: the public API is a small set of `public` classes; everything else is package-private and free to change.

---

## A typical backend project layout

When you reach Spring Boot in later modules, the convention will look something like this:

```text
src/main/java/com/acme/shop/
├── ShopApplication.java         ← entry point
├── controller/
│   ├── OrderController.java
│   └── CustomerController.java
├── service/
│   ├── OrderService.java
│   └── PricingService.java
├── repository/
│   ├── OrderRepository.java
│   └── CustomerRepository.java
├── model/
│   ├── Order.java
│   └── Customer.java
├── dto/
│   └── OrderRequest.java
├── config/
│   └── AppConfig.java
└── exception/
    ├── NotFoundException.java
    └── GlobalExceptionHandler.java
```

You don't need to memorize this now. The point is that real projects group by *role* — controllers, services, repositories — and each role gets its own package. That structure is what you'll be writing toward.

---

## Common pitfalls

- **Package declaration doesn't match folder path.** `Order.java` lives in `src/com/acme/shop/` but says `package com.acme.shop.pricing;`. The compiler will refuse, or worse, it compiles but the classloader can't find it at runtime.
- **`import` typos.** Most IDEs auto-add imports. If you copy code off the web and the import is wrong (importing `java.util.Date` when you wanted `java.sql.Date`), you get bizarre behavior.
- **Wildcard imports hiding clashes.** `import java.util.*;` and `import java.sql.*;` both define a `Date` class. The compiler then complains, "reference to Date is ambiguous."
- **Forgetting that the default package can't be imported.** A class with no `package` statement can't be `import`ed by a class that has one. That's why every real project starts with a package.

---

## Code examples

This module's code uses the default package (no `package` statement) because every file is a standalone single-file launch. To see a real package layout in action, see module 11 of the broader roadmap or any Spring Boot tutorial.

1. `ImportingFromStdlib.java` — a small program that imports `ArrayList`, `HashMap`, and `LocalDate`. Demonstrates named imports vs. fully-qualified names.
2. `StaticImport.java` — `import static java.lang.Math.*` so you can write `sqrt(x)` instead of `Math.sqrt(x)`. Includes a note on when this hurts readability.

---

## Try this yourself

1. In `ImportingFromStdlib.java`, remove the `import java.util.ArrayList;` line. Use the fully-qualified name `java.util.ArrayList<String>` instead. Confirm both produce the same result.
2. Set up a tiny two-package project on disk: `src/foo/A.java` and `src/bar/B.java`. Make `A` import and use `B`. Compile with `javac -d build src/foo/A.java src/bar/B.java`. This is the layout every real project has under the hood.
3. Try `import java.util.*;` and `import java.sql.*;` together, then declare a `Date` variable. Read the compile error. Fix it by replacing one of the wildcards with the specific named import you want.

---

## Self-check

1. Why does Java enforce that package declarations match the folder structure?
2. What's the difference between `import java.util.*;` and `import java.util.ArrayList;`? When would you reach for each?
3. A class with no `package` statement is in the "default package." Why is that fine for tiny demos but unworkable for a real project?
