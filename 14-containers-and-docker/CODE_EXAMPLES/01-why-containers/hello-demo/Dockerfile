# The image carries the exact runtime the program was built for: Linux + a Java 21 JRE.
FROM eclipse-temurin:21-jre
COPY out/ /app/
CMD ["java", "-cp", "/app", "Hello"]
