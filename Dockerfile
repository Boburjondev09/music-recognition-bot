FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache ffmpeg yt-dlp

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/target/music-recognition-bot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
