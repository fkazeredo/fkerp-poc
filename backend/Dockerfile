# syntax=docker/dockerfile:1

# --- Build stage (Maven wrapper only, per CLAUDE.md) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN chmod +x mvnw
COPY config ./config
COPY src ./src
RUN ./mvnw -q -B -DskipTests clean package

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
