package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.bobur.musicbot.service.MusicSearchSessionService;

import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramMessageSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramMessageSender.class);

    private final TelegramClient telegramClient;

    public TelegramMessageSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendText(long chatId, String text) {
        sendAndReturn(chatId, text);
    }

    public Integer sendAndReturn(long chatId, String text) {
        return sendAndReturn(chatId, text, null);
    }

    public Integer sendAndReturn(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text(text);

        if (markup != null) {
            builder.replyMarkup(markup);
        }

        try {
            Message sent = telegramClient.execute(builder.build());
            return sent == null ? null : sent.getMessageId();
        } catch (TelegramApiException exception) {
            log.error("Telegram xabarini yuborib bo‘lmadi. chatId={}", chatId, exception);
            return null;
        }
    }

    public boolean editMessage(
            long chatId,
            Integer messageId,
            String text,
            InlineKeyboardMarkup markup
    ) {
        if (messageId == null) {
            return false;
        }

        try {
            telegramClient.execute(
                    EditMessageText.builder()
                            .chatId(Long.toString(chatId))
                            .messageId(messageId)
                            .text(text)
                            .replyMarkup(markup)
                            .build()
            );
            return true;
        } catch (TelegramApiException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains("message is not modified")) {
                return true;
            }

            log.warn(
                    "Telegram xabarini edit qilib bo‘lmadi. chatId={}, messageId={}",
                    chatId,
                    messageId,
                    exception
            );
            return false;
        }
    }

    public boolean sendAudio(
            long chatId,
            byte[] content,
            String fileName,
            String title,
            String performer
    ) {
        if (!validUpload(content, fileName, "audio")) {
            return false;
        }

        SendAudio audio = SendAudio.builder()
                .chatId(Long.toString(chatId))
                .audio(new InputFile(new ByteArrayInputStream(content), fileName))
                .title(limitMetadata(title, 64))
                .performer(limitMetadata(performer, 64))
                .build();

        try {
            telegramClient.execute(audio);
            log.info(
                    "Telegram audio yuborildi. chatId={}, file={}, sizeMb={}",
                    chatId,
                    fileName,
                    formatSizeMb(content.length)
            );
            return true;
        } catch (TelegramApiException exception) {
            logUploadFailure("audio", chatId, fileName, content.length, exception);

            // Telegram sendAudio media metadata/container sabab rad qilsa,
            // tayyor MP3 yo‘qolib ketmasligi uchun document sifatida fallback qilamiz.
            // Timeout bo‘lsa avtomatik qayta yubormaymiz: birinchi upload Telegramga yetib
            // borgan bo‘lishi mumkin va duplicate hosil qilishi mumkin.
            if (!isTimeout(exception)) {
                return sendDocumentInternal(
                        chatId,
                        content,
                        fileName,
                        title == null ? "🎵 MP3" : "🎵 " + limitMetadata(title, 900)
                );
            }
            return false;
        }
    }

    public Integer sendMusicSearchResults(
            long chatId,
            String text,
            MusicSearchSessionService.SearchSession session
    ) {
        return sendAndReturn(chatId, text, buildSearchResultsKeyboard(session));
    }

    public boolean editMusicSearchResults(
            long chatId,
            Integer messageId,
            String text,
            MusicSearchSessionService.SearchSession session
    ) {
        return editMessage(chatId, messageId, text, buildSearchResultsKeyboard(session));
    }

    public boolean editMusicSearchSettings(
            long chatId,
            Integer messageId,
            String text,
            MusicSearchSessionService.SearchSession session
    ) {
        return editMessage(chatId, messageId, text, buildSettingsKeyboard(session));
    }

    /**
     * Tanlangan trek oynasi: faqat MP3, Similar va Back.
     */
    public boolean editMusicSearchTrackOptions(
            long chatId,
            Integer messageId,
            String text,
            MusicSearchSessionService.SearchSession session,
            int globalIndex
    ) {
        InlineKeyboardRow mp3Row = new InlineKeyboardRow(
                styledButton(
                        "🎵 MP3",
                        "ma:" + session.id() + ":" + globalIndex,
                        "success"
                )
        );

        InlineKeyboardRow similarRow = new InlineKeyboardRow(
                button(
                        "🔁 Similar",
                        "mr:" + session.id() + ":" + globalIndex
                )
        );

        InlineKeyboardRow backRow = new InlineKeyboardRow(
                button(
                        session.theme().previous() + " Back",
                        "mb:" + session.id()
                )
        );

        return editMessage(
                chatId,
                messageId,
                text,
                new InlineKeyboardMarkup(List.of(mp3Row, similarRow, backRow))
        );
    }

    /**
     * Text-search natijasidagi MP3 tugmasi bosilganda bitrate tanlash oynasi.
     */
    public boolean editMusicSearchAudioQualityOptions(
            long chatId,
            Integer messageId,
            MusicSearchSessionService.SearchSession session,
            int globalIndex
    ) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        styledButton(
                                "128 kbps",
                                "mak:" + session.id() + ":" + globalIndex + ":128",
                                "primary"
                        ),
                        styledButton(
                                "192 kbps",
                                "mak:" + session.id() + ":" + globalIndex + ":192",
                                "primary"
                        )
                ),
                new InlineKeyboardRow(
                        styledButton(
                                "256 kbps",
                                "mak:" + session.id() + ":" + globalIndex + ":256",
                                "primary"
                        ),
                        styledButton(
                                "320 kbps",
                                "mak:" + session.id() + ":" + globalIndex + ":320",
                                "success"
                        )
                ),
                new InlineKeyboardRow(
                        styledButton(
                                session.theme().previous() + " Back",
                                "ms:" + session.id() + ":" + globalIndex,
                                "primary"
                        )
                )
        ));

        return editMessage(
                chatId,
                messageId,
                "🎵 MP3 sifatini tanlang:\n\nQancha kbps da yuklab olmoqchisiz?",
                markup
        );
    }

    public void answerCallbackQuery(String callbackQueryId) {
        try {
            telegramClient.execute(
                    AnswerCallbackQuery.builder()
                            .callbackQueryId(callbackQueryId)
                            .build()
            );
        } catch (TelegramApiException exception) {
            log.debug("Callback query'ga javob berib bo‘lmadi. id={}", callbackQueryId);
        }
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        try {
            telegramClient.execute(
                    AnswerCallbackQuery.builder()
                            .callbackQueryId(callbackQueryId)
                            .text(text)
                            .build()
            );
        } catch (TelegramApiException exception) {
            log.debug("Callback query'ga matnli javob berib bo‘lmadi. id={}", callbackQueryId);
        }
    }

    public void deleteMessage(long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }

        try {
            telegramClient.execute(
                    DeleteMessage.builder()
                            .chatId(Long.toString(chatId))
                            .messageId(messageId)
                            .build()
            );
        } catch (TelegramApiException exception) {
            log.debug(
                    "Telegram xabarini o‘chirib bo‘lmadi. chatId={}, messageId={}",
                    chatId,
                    messageId
            );
        }
    }

    private InlineKeyboardMarkup buildSearchResultsKeyboard(
            MusicSearchSessionService.SearchSession session
    ) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        var pageResults = session.currentPageResults();
        int pageStart = session.pageStartIndex();

        for (int start = 0; start < pageResults.size(); start += 5) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            int end = Math.min(start + 5, pageResults.size());

            for (int localIndex = start; localIndex < end; localIndex++) {
                int globalIndex = pageStart + localIndex;
                row.add(
                        button(
                                String.valueOf(localIndex + 1),
                                "ms:" + session.id() + ":" + globalIndex
                        )
                );
            }
            rows.add(row);
        }

        int previousPage = Math.max(0, session.page() - 1);
        int nextPage = Math.min(
                Math.max(0, session.totalPages() - 1),
                session.page() + 1
        );

        rows.add(new InlineKeyboardRow(
                styledButton(
                        session.theme().previous(),
                        "mp:" + session.id() + ":" + previousPage,
                        "primary"
                ),
                styledButton(
                        session.theme().close(),
                        "mx:" + session.id(),
                        "danger"
                ),
                styledButton(
                        session.theme().next(),
                        "mp:" + session.id() + ":" + nextPage,
                        "primary"
                )
        ));

        // Audio format/quality global settings olib tashlandi.
        // Har bir MP3 download oldidan bitrate alohida tanlanadi.
        rows.add(new InlineKeyboardRow(
                button(session.theme().settings(), "mg:" + session.id()),
                button(session.sort().label(), "mo:" + session.id())
        ));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardMarkup buildSettingsKeyboard(
            MusicSearchSessionService.SearchSession session
    ) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                themeButton(
                        "Default",
                        "mt:" + session.id() + ":D",
                        session.theme() == uz.bobur.musicbot.domain.search.SearchTheme.DEFAULT
                ),
                themeButton(
                        "Minimal",
                        "mt:" + session.id() + ":M",
                        session.theme() == uz.bobur.musicbot.domain.search.SearchTheme.MINIMAL
                )
        ));

        rows.add(new InlineKeyboardRow(
                themeButton(
                        "Pixel",
                        "mt:" + session.id() + ":P",
                        session.theme() == uz.bobur.musicbot.domain.search.SearchTheme.PIXEL
                ),
                themeButton(
                        "Music",
                        "mt:" + session.id() + ":U",
                        session.theme() == uz.bobur.musicbot.domain.search.SearchTheme.MUSIC
                )
        ));

        rows.add(new InlineKeyboardRow(
                button(
                        "↕ Sort: " + session.sort().label(),
                        "so:" + session.id()
                )
        ));

        rows.add(new InlineKeyboardRow(
                styledButton(
                        session.theme().previous() + " Back",
                        "mb:" + session.id(),
                        "primary"
                ),
                styledButton(
                        session.theme().close() + " Close",
                        "mx:" + session.id(),
                        "danger"
                )
        ));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardButton themeButton(
            String text,
            String callbackData,
            boolean selected
    ) {
        return styledButton(
                (selected ? "✅ " : "") + text,
                callbackData,
                selected ? "success" : null
        );
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private InlineKeyboardButton styledButton(
            String text,
            String callbackData,
            String style
    ) {
        var builder = InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData);

        if (style != null && !style.isBlank()) {
            builder.style(style);
        }

        return builder.build();
    }

    private boolean sendDocumentInternal(
            long chatId,
            byte[] content,
            String fileName,
            String caption
    ) {
        if (!validUpload(content, fileName, "document")) {
            return false;
        }

        try {
            telegramClient.execute(
                    SendDocument.builder()
                            .chatId(Long.toString(chatId))
                            .document(new InputFile(new ByteArrayInputStream(content), fileName))
                            .caption(limitMetadata(caption, 1024))
                            .build()
            );

            log.info(
                    "Telegram document yuborildi. chatId={}, file={}, sizeMb={}",
                    chatId,
                    fileName,
                    formatSizeMb(content.length)
            );
            return true;
        } catch (TelegramApiException exception) {
            logUploadFailure("document", chatId, fileName, content.length, exception);
            return false;
        }
    }

    private boolean validUpload(byte[] content, String fileName, String type) {
        if (content == null || content.length == 0) {
            log.warn("Telegram {} yuborilmadi: fayl bo‘sh. file={}", type, fileName);
            return false;
        }
        return true;
    }

    private void logUploadFailure(
            String type,
            long chatId,
            String fileName,
            long bytes,
            TelegramApiException exception
    ) {
        Throwable root = rootCause(exception);

        log.error(
                "Telegram {} upload xatosi. chatId={}, file={}, sizeMb={}, error={}, root={} : {}",
                type,
                chatId,
                fileName,
                formatSizeMb(bytes),
                exception.getMessage(),
                root.getClass().getSimpleName(),
                root.getMessage(),
                exception
        );
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String limitMetadata(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private String formatSizeMb(long bytes) {
        return "%.2f".formatted(bytes / (1024d * 1024d));
    }
}