# syntax=docker/dockerfile:1.6

# -------- Stage 1 : Build Maven ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

# -------- Stage 2 : Run Java ----------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

COPY --from=build /app/target/*-jar-with-dependencies.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
