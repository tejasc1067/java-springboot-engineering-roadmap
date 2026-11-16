# The working image: exec-form ENTRYPOINT (java is PID 1 and receives SIGTERM) + a HEALTHCHECK.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/app.jar app.jar
EXPOSE 8080
# eclipse-temurin:21-jre includes curl. Slimmer images (distroless, topic 10) don't - see the topic.
HEALTHCHECK --interval=2s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
