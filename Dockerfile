FROM openjdk:21-jdk-slim AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/url-shortener-0.0.1-SNAPSHOT.jar"]