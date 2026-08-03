# Music Recognition Telegram Bot

🔗 Live: [@shazam_r_bot](https://t.me/shazam_r_bot)

Java 21 va Spring Boot asosidagi Telegram Long Polling bot. Foydalanuvchi voice, audio, kichik video, media-document yoki YouTube havolasini yuboradi; servis faylni Telegram'dan yuklab oladi (yoki YouTube havolasi bo‘lsa `yt-dlp` orqali audio ajratib oladi), ACRCloud orqali qo‘shiqni aniqlaydi, natijani PostgreSQL'ga yozadi va MinIO yoqilgan bo‘lsa original media faylni saqlaydi.

## Texnologiyalar

- Java 21
- Spring Boot 3.5.16
- TelegramBots Long Polling 10.0.0
- ACRCloud Audio Recognition API
- yt-dlp + ffmpeg (YouTube havolalaridan audio ajratib olish)
- PostgreSQL + Flyway
- MinIO Java SDK
- Docker / Docker Compose

## Bot komandalar

- `/start` — bot haqida ma’lumot
- `/help` — foydalanish bo‘yicha yordam
- `/history` — oxirgi qidiruvlar
- `/clear_history` — foydalanuvchining tarixini o‘chirish

## Qo‘llab-quvvatlanadigan xabarlar

- Telegram voice
- Audio fayl
- Video va video-note
- Audio/video MIME turidagi document
- YouTube video havolasi (matn xabar ichida) — `youtube.com/watch?v=...`, `youtu.be/...`, `youtube.com/shorts/...`

Botning standart fayl hajmi limiti 10 MB. YouTube havolasi uchun standart video davomiylik limiti 10 daqiqa (`app.youtube.max-duration-seconds`).

## Tokenlar

1. Telegram'da `@BotFather` orqali bot yarating va token oling.
2. ACRCloud consoleda ("Audio & Video Recognition" > "Identify") yangi project yarating va host, access key, access secret oling: https://console.acrcloud.com/
3. `.env.example` faylini `.env` nomi bilan nusxalang.
4. Token va credential qiymatlarini kiriting.

```bash
cp .env.example .env
```

## Docker Compose bilan ishga tushirish

```bash
docker compose up --build -d
```

Loglarni ko‘rish:

```bash
docker compose logs -f app
```

To‘xtatish:

```bash
docker compose down
```

Ma’lumotlar volume bilan saqlanadi. Volume'larni ham o‘chirish uchun:

```bash
docker compose down -v
```

## IntelliJ IDEA orqali lokal ishga tushirish

Avval PostgreSQL va MinIO'ni ishga tushiring:

```bash
docker compose up -d postgres minio
```

Environment variables:

```text
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=...
ACRCLOUD_HOST=identify-eu-west-1.acrcloud.com
ACRCLOUD_ACCESS_KEY=...
ACRCLOUD_ACCESS_SECRET=...
DB_URL=jdbc:postgresql://localhost:5432/music_recognition
DB_USERNAME=music_user
DB_PASSWORD=music_password
MINIO_ENABLED=true
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
MINIO_BUCKET=music-audio
YOUTUBE_LINK_ENABLED=true
YOUTUBE_MAX_DURATION_SECONDS=600
YOUTUBE_DOWNLOAD_TIMEOUT=300s
```

YouTube havolalarini lokal (Docker'siz) sinash uchun kompyuteringizda `yt-dlp` va `ffmpeg` PATH'da bo‘lishi kerak (`pip install yt-dlp`, `ffmpeg` alohida o‘rnatiladi). Docker orqali ishga tushirilganda ular image ichida allaqachon mavjud.

So‘ng:

```bash
mvn clean spring-boot:run
```

## MinIO'ni o‘chirish

Audio faylni MinIO'da saqlash talab qilinmasa:

```text
MINIO_ENABLED=false
```

Recognition va PostgreSQL history ishlashda davom etadi.

## YouTube havolasini o‘chirish

YouTube link orqali aniqlashni o‘chirish uchun:

```text
YOUTUBE_LINK_ENABLED=false
```

Bu holatda foydalanuvchi YouTube link yuborsa, bot audio/voice fayl yuborishni so‘raydi.

## Health endpoint

```text
GET http://localhost:8080/actuator/health
```

## Production tavsiyalari

- Telegram va ACRCloud tokenlarini Git'ga commit qilmang.
- Tokenlarni Vault, Kubernetes Secret yoki CI/CD protected variables'da saqlang.
- PostgreSQL parolini almashtiring.
- MinIO credentiallarini almashtiring va bucket policy'ni private qoldiring.
- `app.processing.max-concurrent-jobs` qiymatini server resursiga moslang.
- ACRCloud billing va request limitlarini kuzating.
