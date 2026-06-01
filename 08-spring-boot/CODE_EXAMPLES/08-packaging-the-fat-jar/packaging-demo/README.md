# packaging-demo

Demonstrates `spring-boot-maven-plugin` producing a runnable fat jar.

## Run

```bash
mvn -q test                                          # context loads
mvn -q clean package                                 # produces target/packaging-demo-1.0.0.jar
java -jar target/packaging-demo-1.0.0.jar            # runs the fat jar
java -jar target/packaging-demo-1.0.0.jar --app.message="From CLI override"
```

## What to notice

- `target/packaging-demo-1.0.0.jar` is the executable fat jar.
- `target/packaging-demo-1.0.0.jar.original` is the plain jar with only your classes — it does not run.
- Unzip the fat jar (`jar -tf target/packaging-demo-1.0.0.jar | head`) and observe `BOOT-INF/classes/`, `BOOT-INF/lib/`, and the launcher under `org/springframework/boot/loader/`.
- `META-INF/MANIFEST.MF` points at the launcher, not at `App`. Boot's loader then locates your `Start-Class` (also in the manifest) and runs its `main`.
