package uz.bobur.musicbot.bot;

import org.springframework.stereotype.Component;
import uz.bobur.musicbot.domain.MusicSearchResult;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.domain.SearchHistoryView;
import uz.bobur.musicbot.enums.RecognitionStatus;
import uz.bobur.musicbot.service.MusicSearchSessionService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class BotMessageFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Tashkent"));

    private static final int SEARCH_TITLE_LIMIT = 82;

    public String rateLimitExceeded() {
        return "Juda ko‘p so‘rov yubordingiz. Iltimos, birozdan keyin qayta urinib ko‘ring.";
    }

    public String adminOnly() {
        return "Bu komanda faqat admin uchun mavjud.";
    }

    public String start(String firstName, boolean isAdmin, boolean musicSearchEnabled, long maxFileSizeBytes) {
        String name = firstName == null || firstName.isBlank()
                ? ""
                : ", " + firstName;

        String adminCommands = isAdmin
                ? "/history — oxirgi qidiruvlar\n/clear_history — tarixni o‘chirish\n"
                : "";

        String usage = musicSearchEnabled
                ? """
                Foydalanish:
                1. 10–20 soniyalik aniq audio/video parcha yuboring; yoki
                2. Masalan: Arctic Monkeys I Wanna Be Yours deb yozing.
                3. Media fayl %s dan katta bo‘lmasin.

                Matn qidiruvida natijalar 10 tadan sahifalanadi. ⬅️/➡️ bilan sahifalarni almashtirish, sort va ko‘rinish sozlamalarini tanlash mumkin.

                MP3 tugmasi faqat download ruxsati bor tashqi audio manbada aynan shu trek topilganda ishlaydi.
                """.formatted(humanFileSize(maxFileSizeBytes))
                : """
                Foydalanish:
                10–20 soniyalik aniq audio/video parcha yuboring — men qo‘shiqni ACRCloud orqali aniqlayman.
                Media fayl %s dan katta bo‘lmasin.

                Matn orqali qidiruv hozircha o‘chirilgan.
                """.formatted(humanFileSize(maxFileSizeBytes));

        return """
                Assalomu alaykum%s!

                Men voice/audio/video ichidagi qo‘shiqni ACRCloud orqali aniqlayman.

                %s
                Komandalar:
                %s/help — yordam
                """.formatted(name, usage, adminCommands);
    }

    public String help(boolean musicSearchEnabled, long maxFileSizeBytes) {
        String base = """
                Qo‘llab-quvvatlanadi:
                • Telegram voice
                • MP3, WAV, OGG, M4A va boshqa audio fayllar
                • %s gacha qisqa video/media — faqat qo‘shiqni aniqlash uchun
                """.formatted(humanFileSize(maxFileSizeBytes));

        String searchSection = musicSearchEnabled
                ? """

                • Matn orqali qidiruv — masalan: Ed Sheeran Perfect

                Matn qidiruvi:
                • Apple iTunes Search API orqali qidiruv
                • 10 tadan pagination
                • ⬅️ / ➡️ oldingi va keyingi sahifa
                • Relevance / Title / Duration bo‘yicha sort
                • 🎨 tugmasi orqali tugmalar ko‘rinishi
                • MP3 uchun: 128k / 192k / 256k / 320k
                """
                : """

                Matn orqali qidiruv hozircha o‘chirilgan.
                """;

        return (base + searchSection + """

                Link orqali qidirish va video download funksiyasi yo‘q.

                Recognition uchun musiqa baland va fon shovqini kam bo‘lsin. Odatda 10–20 soniyalik parcha yetarli.
                """).trim();
    }

    public String fileTooLarge(long maxFileSizeBytes) {
        return "Fayl %s limitdan katta. Iltimos, qisqaroq audio yoki video parcha yuboring."
                .formatted(humanFileSize(maxFileSizeBytes));
    }

    public String recognized(RecognitionResult result) {
        StringBuilder message = new StringBuilder("🎵 Qo‘shiq aniqlandi!\n\n");
        append(message, "Nomi", result.title());
        append(message, "Ijrochi", result.artist());
        append(message, "Albom", result.album());
        append(message, "Sana", result.releaseDate());
        append(message, "Label", result.label());
        append(message, "Timecode", result.timecode());
                append(message, "Apple Music", result.appleMusicUrl());
        return message.toString().trim();
    }

    public String musicSearchNotFound(String query) {
        return "🔎 \"%s\" bo‘yicha qo‘shiq topilmadi."
                .formatted(limit(query, 120));
    }

    public String musicSearchResults(MusicSearchSessionService.SearchSession session) {
        List<MusicSearchResult> pageResults = session.currentPageResults();
        if (pageResults.isEmpty()) {
            return musicSearchNotFound(session.query());
        }

        int total = session.totalResults();
        int first = session.pageStartIndex() + 1;
        int last = Math.min(session.pageStartIndex() + pageResults.size(), total);
        int totalPages = Math.max(1, session.totalPages());

        StringBuilder message = new StringBuilder();
        message.append("🔎 ")
                .append(limit(session.query(), 120))
                .append('\n');

        message.append("Results ")
                .append(first)
                .append('-')
                .append(last)
                .append(" of ")
                .append(total)
                .append('\n');

        message.append("Page ")
                .append(session.page() + 1)
                .append('/')
                .append(totalPages)
                .append(" • ")
                .append(session.sort().label())
                .append("\n\n");

        for (int localIndex = 0; localIndex < pageResults.size(); localIndex++) {
            MusicSearchResult item = pageResults.get(localIndex);

            message.append(localIndex + 1).append(". ");

            if (hasText(item.artist())
                    && !safeLower(item.title()).contains(safeLower(item.artist()))) {
                message.append(limit(item.artist(), 38)).append(" — ");
            }

            message.append(limit(item.title(), SEARCH_TITLE_LIMIT));

            if (item.durationSeconds() > 0) {
                message.append("  ").append(formatDuration(item.durationSeconds()));
            }

            message.append('\n');
        }

        message.append("\n🎵 Trekni tanlang — MP3 sifati keyin tanlanadi.");
        return message.toString().trim();
    }

    public String musicSearchResults(String query, List<MusicSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return musicSearchNotFound(query);
        }

        StringBuilder message = new StringBuilder();
        message.append("🔎 ").append(limit(query, 120)).append('\n');
        message.append("Results 1-")
                .append(results.size())
                .append(" of ")
                .append(results.size())
                .append("\n\n");

        for (int i = 0; i < results.size(); i++) {
            MusicSearchResult item = results.get(i);
            message.append(i + 1)
                    .append(". ")
                    .append(limit(item.title(), SEARCH_TITLE_LIMIT));

            if (item.durationSeconds() > 0) {
                message.append("  ").append(formatDuration(item.durationSeconds()));
            }
            message.append('\n');
        }

        return message.toString().trim();
    }

    public String selectedTrack(
            MusicSearchResult result,
            MusicSearchSessionService.SearchSession session
    ) {
        StringBuilder message = new StringBuilder("🎵 Tanlangan trek\n\n");
        append(message, "Nomi", result.title());
        append(message, "Ijrochi", result.artist());
        append(message, "Albom", result.album());

        if (result.durationSeconds() > 0) {
            append(message, "Davomiyligi", formatDuration(result.durationSeconds()));
        }

        message.append("\nMP3 yuklash yoki similar qo‘shiqlarni tanlang.");
        return message.toString().trim();
    }

    public String searchSettings(MusicSearchSessionService.SearchSession session) {
        return """
                ⚙️ Settings

                What would you like to change?

                🎨 Theme: %s
                ↕ Sort: %s

                MP3 sifati trekni yuklash paytida alohida tanlanadi.
                """.formatted(
                session.theme().label(),
                session.sort().label()
        ).trim();
    }

    public String history(List<SearchHistoryView> items) {
        if (items.isEmpty()) {
            return "Qidiruv tarixi hozircha bo‘sh.";
        }

        StringBuilder result = new StringBuilder("Oxirgi qidiruvlaringiz:\n\n");

        for (int index = 0; index < items.size(); index++) {
            SearchHistoryView item = items.get(index);
            result.append(index + 1)
                    .append(". ")
                    .append(formatHistoryItem(item))
                    .append("\n   ")
                    .append(DATE_FORMATTER.format(item.createdAt()))
                    .append("\n\n");
        }

        return result.toString().trim();
    }

    private String formatHistoryItem(SearchHistoryView item) {
        if (item.status() == RecognitionStatus.RECOGNIZED) {
            return safe(item.artist()) + " — " + safe(item.title());
        }
        if (item.status() == RecognitionStatus.NOT_FOUND) {
            return "Qo‘shiq aniqlanmadi";
        }
        if (item.status() == RecognitionStatus.PROCESSING) {
            return "Qayta ishlanmoqda";
        }
        return "Xatolik: " + safe(item.errorMessage());
    }

    private void append(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label)
                    .append(": ")
                    .append(value)
                    .append('\n');
        }
    }

    private String humanFileSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            long kb = Math.max(1, bytes / 1024L);
            return kb + " KB";
        }

        double mb = bytes / (1024d * 1024d);
        return mb == Math.rint(mb)
                ? "%.0f MB".formatted(mb)
                : "%.1f MB".formatted(mb);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
        }
        return "%d:%02d".formatted(minutes, remainingSeconds);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Ma’lumot yo‘q" : value;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}