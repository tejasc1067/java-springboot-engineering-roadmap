# injection-styles-demo

The same `Greeter` three times: constructor, setter, and field injection. The four test classes are where the lesson lives -- each one tries to construct one variant *without* Spring and reports how easy that was.

Run:

```bash
mvn -q test                  # runs all four test classes; one test is @Disabled with a note
mvn -q compile exec:java     # boots Spring; all three Greeters work identically when wired
```

Read in this order: `MessageFormatter`, `UppercaseFormatter`, then the three Greeters, then the test files. The diff between `ConstructorGreeterTest` and `FieldGreeterTest` is the point of the topic.
