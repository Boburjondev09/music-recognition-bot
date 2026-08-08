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

# Fallback defaults so the image also runs standalone (e.g. IntelliJ "Dockerfile" run
# configuration) against the postgres/minio started by docker-compose, reachable via the
# host's published ports through Docker Desktop's host.docker.internal. docker-compose.yml
# always overrides these with the internal service hostnames when running the full stack.
ENV DB_URL=jdbc:postgresql://host.docker.internal:5433/music_recognition
ENV DB_USERNAME=music_user
ENV DB_PASSWORD=music_password
ENV MINIO_ENABLED=true
ENV MINIO_ENDPOINT=http://host.docker.internal:9000

COPY --from=build /workspace/target/music-recognition-bot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
