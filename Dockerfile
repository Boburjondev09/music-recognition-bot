# =========================
# BUILD STAGE
# =========================

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests clean package


# =========================
# RUNTIME STAGE
# =========================

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app


# yt-dlp + ffmpeg:
# YouTube'dan qo‘shiqni topib, MP3 formatga
# ajratib olish uchun ishlatiladi.
#
# py3-pip + bgutil-ytdlp-pot-provider:
# YouTube endi haqiqiy audio/video baytlarini berishdan oldin
# "PO token" talab qiladi (bo'lmasa HTTP 403 qaytaradi). Bu plagin
# yt-dlp'ni pot-provider konteyneridagi token serverga ulaydi.
RUN apk add --no-cache \
        ffmpeg \
        yt-dlp \
        py3-pip \
        ca-certificates \
    && pip install --no-cache-dir --break-system-packages bgutil-ytdlp-pot-provider \
    && echo "=== ffmpeg ===" \
    && ffmpeg -version | head -n 1

# Alpine's packaged yt-dlp lags months behind YouTube's own anti-bot changes.
# Overwrite it with a pinned nightly build that ships current YouTube player
# clients (e.g. "visionos") not yet subject to the PO-token/GVS enforcement
# that blocks official music videos on older yt-dlp versions.
RUN wget -q -O /usr/bin/yt-dlp \
        https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/download/2026.08.18.122307/yt-dlp \
    && chmod +x /usr/bin/yt-dlp \
    && echo "=== yt-dlp ===" \
    && yt-dlp --version


COPY --from=build \
    /workspace/target/*.jar \
    /app/app.jar


EXPOSE 8080


ENTRYPOINT ["java", "-jar", "/app/app.jar"]