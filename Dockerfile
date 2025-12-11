# -------- Stage 1 : Build Maven ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests clean package

# -------- Stage 2 : Run Java ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*-jar-with-dependencies.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
