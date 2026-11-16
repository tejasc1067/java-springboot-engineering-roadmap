# syntax=docker/dockerfile:1
# MULTI-STAGE: stage 1 builds the jar, stage 2 runs it. Only what stage 2 copies ends up in the image.

# ---- Stage 1: build (Maven + full JDK; thrown away afterwards) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# A BuildKit cache mount keeps ~/.m2 between builds, so dependencies download once, not on every change.
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests

# ---- Stage 2: run (just a JRE + the jar) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
