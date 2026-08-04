package uz.bobur.musicbot.bot;

import org.springframework.stereotype.Component;
import uz.bobur.musicbot.domain.RecognitionResult;
import uz.bobur.musicbot.enums.RecognitionStatus;
import uz.bobur.musicbot.domain.SearchHistoryView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BotMessageFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Tashkent"));

    public String start(String firstName, boolean isAdmin) {
        String name = firstName == null || firstName.isBlank() ? "" : ", " + firstName;
        String adminCommands = isAdmin ? "/history — oxirgi qidiruvlar\n/clear_history — tarixni o‘chirish\n" : "";
        return """
                Assalomu alaykum%s!

                Men yuborilgan voice, audio, qisqa video yoki YouTube havolasidagi qo‘shiqni aniqlayman.

                Foydalanish:
                1. 10–20 soniyalik aniq parcha yuboring yoki YouTube link tashlang.
                2. Fayl 10 MB dan katta bo‘lmasin.
                3. Natijani kuting.

                Komandalar:
                %s/help — yordam
                """.formatted(name, adminCommands);
    }

    public String help() {
        return """
                Qo‘llab-quvvatlanadi:
                • Telegram voice
                • MP3, WAV, OGG, M4A va boshqa audio fayllar
                • 10 MB gacha qisqa video
                • YouTube video havolasi (link) — "Audio" yoki "Video" tugmasi orqali to‘liq faylni yuklab olish mumkin
                
                Yaxshi natija uchun musiqa baland va fon shovqini kam bo‘lsin. Odatda 10–20 soniyalik parcha yetarli.
                """;
    }

    public String recognized(RecognitionResult result) {
        StringBuilder message = new StringBuilder("🎵 Qo‘shiq aniqlandi!\n\n");
        append(message, "Nomi", result.title());
        append(message, "Ijrochi", result.artist());
        append(message, "Albom", result.album());
        append(message, "Sana", result.releaseDate());
        append(message, "Label", result.label());
        append(message, "Timecode", result.timecode());
        append(message, "Qo‘shiq", result.songLink());
        append(message, "Spotify", result.spotifyUrl());
        append(message, "Apple Music", result.appleMusicUrl());
        return message.toString().trim();
    }

    public String history(List<SearchHistoryView> items) {
        if (items.isEmpty()) {
            return "Qidiruv tarixi hozircha bo‘sh.";
        }

        StringBuilder result = new StringBuilder("Oxirgi qidiruvlaringiz:\n\n");
        for (int index = 0; index < items.size(); index++) {
            SearchHistoryView item = items.get(index);
            result.append(index + 1).append(". ").append(formatHistoryItem(item)).append("\n   ").append(DATE_FORMATTER.format(item.createdAt())).append("\n\n");
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
            builder.append(label).append(": ").append(value).append('\n');
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Ma’lumot yo‘q" : value;
    }
}
