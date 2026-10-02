FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system sharenote && useradd --system --gid sharenote sharenote
COPY --from=build /app/target/sharenote-0.0.1-SNAPSHOT.jar app.jar
USER sharenote
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
