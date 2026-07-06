# 02 — Object-Oriented Programming and Design Principles

Java is fundamentally an object-oriented language. The Spring ecosystem you'll meet in modules 07-10 assumes you can think in objects, model relationships, design interfaces, and reason about coupling. This module builds that thinking.

By the end you won't just know "what an `abstract class` is." You'll know when to reach for one vs. an interface, when inheritance is the wrong tool, and what "good" object-oriented design looks like in real backend code.

## Who this is for

- You've finished module 01 (or are comfortable with basic Java syntax: variables, methods, loops, classes-as-data-holders).
- You can write a Java program with a `main` method and a few methods.

If "class" and "object" still feel unclear, work through module 01 first.

## What you'll be able to do at the end

Concrete outcomes. Check each off as you go.

- [ ] Explain the difference between a class and an object without reaching for analogies.
- [ ] Write a properly encapsulated class and explain *why* fields shouldn't be `public`.
- [ ] Decide between inheritance and composition for a given design problem, and justify the choice.
- [ ] Implement `equals()` and `hashCode()` together, and explain why one without the other is a bug.
- [ ] Make a class immutable and explain three things you had to do beyond just adding `final`.
- [ ] Define each SOLID principle in one sentence and recognize when code violates one.
- [ ] Recognize "this looks like inheritance but should be composition" in code review.

## Prerequisites

Before starting, you should be able to:

- Write a basic Java class with fields and methods (module 01).
- Run a `.java` file from the command line or IntelliJ (no external libraries needed — module 02 uses only the standard library).
- Understand `static` vs. instance methods (module 01).

## How this module is organized

Topics 01–02 cover the building blocks: classes, objects, constructors.

Topics 03–09 are the four pillars of OOP — encapsulation, inheritance, polymorphism, abstraction — plus the supporting tools (`super`, overloading vs. overriding, interfaces).

Topics 10–13 cover the things every Java class secretly does (`Object` methods), relationship modeling (composition vs. aggregation), and one of the most important design ideas in backend code: immutability.

Topic 14 (SOLID) is the bridge into Spring: every Spring pattern you'll later learn assumes you've internalized these five principles.

```text
01-classes-and-objects                       ← the foundation
02-constructors
03-encapsulation                             ← four pillars begin
04-inheritance
05-super-keyword
06-method-overloading-vs-overriding
07-polymorphism
08-abstraction
09-interfaces                                ← four pillars end
10-object-class-methods                      ← what every class secretly has
11-association-aggregation-composition       ← relationships
12-final-keyword-in-oop
13-immutability
14-solid-principles                          ← design for change
```

## How to run the code

No external dependencies — just the JDK. From the repo root:

```bash
java 02-oops-and-design-principles/CODE_EXAMPLES/04-inheritance/AnimalKingdom.java
```

Inside each topic's `CODE_EXAMPLES/` folder, files are named in PascalCase. Reading order is in each topic markdown's "Code examples" section (alphabetical file order does not match reading order). Files with `Broken` in their names are intentionally bad — the next file listed fixes them. Run both, in the markdown's listed order.

## Working through a topic

For each topic file:

1. Read the markdown.
2. Run the first listed code example. Read it. Modify it.
3. Run the next listed example. Compare.
4. Do the "Try this yourself" exercise at the end of the markdown.
5. Answer the "Self-check" questions out loud, in your own words.

If you can't answer all three self-check questions, re-read the topic before moving on. The next topic almost always assumes you have.

## Module checkpoint

Before moving on to module 03a (Java language depth), you should be able to answer these aloud:

1. A junior dev makes every field `public` "for convenience." What's the actual cost, and what should they do instead?
2. You see `class Stack extends ArrayList`. What's wrong with this, and what's the fix?
3. A teammate adds a setter to a class declared `final` with all `final` fields, "for flexibility." Why is this a contradiction?
4. Walk through what happens if you override `equals()` but not `hashCode()`. What breaks, specifically?
5. Define each of S, O, L, I, D in one sentence. Then point to a real Java library/framework that obeys each one.

If those are easy, you're ready. If any feel shaky, the corresponding topic is 03, 04 (or 11), 13, 10, and 14.
