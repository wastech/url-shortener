FROM openjdk:21-jdk-slim AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn/
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

COPY src src

RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]