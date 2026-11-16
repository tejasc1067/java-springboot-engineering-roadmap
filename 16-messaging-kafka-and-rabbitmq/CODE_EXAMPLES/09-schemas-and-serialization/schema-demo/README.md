# schema-demo

What happens to consumers when the producer's event class changes, through an embedded Kafka broker (no Docker),
and the compatibility check a schema registry runs, done with the Avro library.

- `TypeHeaderTest` — `JsonSerializer` writes the producer's class name into `__TypeId__`; the default
  `JsonDeserializer` refuses it (trusted packages); a moved class breaks every record; a fixed target type or a
  type mapping fixes it.
- `FieldEvolutionTest` — an added field is ignored; a renamed field is silently read as `0`; a plain
  `ObjectMapper` breaks on any new field.
- `AvroCompatibilityTest` — which changes are backward and forward compatible, and old bytes read with a new schema.

Run:

```
mvn -q test
```

About 10 seconds. Three `ERROR ... CompletedFetch ... Value Deserializers with error` lines are printed: those are the
deserialization failures the tests expect.
