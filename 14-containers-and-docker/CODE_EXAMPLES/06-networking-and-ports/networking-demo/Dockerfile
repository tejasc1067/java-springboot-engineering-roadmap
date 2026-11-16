FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
# Documentation only: tells readers (and `docker ps`) the app listens on 8080. It publishes nothing.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
