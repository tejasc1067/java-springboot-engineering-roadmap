# conflict-demo

Two real libraries (HikariCP and Kafka clients) pull different versions of `slf4j-api`. This project lets you see the conflict, apply each of the three resolution strategies, and confirm which version wins.

## See the conflict

```bash
mvn -q dependency:tree -Dverbose -Dincludes=org.slf4j:slf4j-api
```

Look for an "omitted for conflict" line. That's the loser.

## Run it

```bash
mvn -q dependency:copy-dependencies -DoutputDirectory=target/lib
mvn -q compile
java -cp "target/classes;target/lib/*" com.example.Demo
```

(On macOS/Linux, replace `;` with `:`.)

The output prints the effective `slf4j-api` version that ended up on the runtime classpath.

## Try the three strategies

In `pom.xml`, three commented blocks are labelled **STRATEGY A / B / C**:

- **A**: declare slf4j-api directly in `<dependencies>` — wins by nearest.
- **B**: pin via `<dependencyManagement>` — wins regardless.
- **C**: `<exclusions>` on the offending library — remove the transitive entirely.

Uncomment one at a time, re-run `mvn dependency:tree -Dverbose`, and watch the winner change.

## The point

Maven's resolution is deterministic but quiet. When the wrong transitive wins, your runtime breaks with `NoSuchMethodError`. The fix is one of three small POM edits — but you have to know to look.
