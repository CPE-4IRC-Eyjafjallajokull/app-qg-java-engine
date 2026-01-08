# -------- Stage 1 : Build Maven (JDK 21) ----------
FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

# -------- Stage 2 : Run Java (JRE 21) ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /app/target/*-jar-with-dependencies.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
