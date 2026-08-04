package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

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
        SendMessage message = SendMessage.builder().chatId(Long.toString(chatId)).text(text).build();

        try {
            Message sent = telegramClient.execute(message);
            return sent == null ? null : sent.getMessageId();
        } catch (TelegramApiException exception) {
            log.error("Telegram xabarini yuborib bo‘lmadi. chatId={}", chatId, exception);
            return null;
        }
    }

    public void sendAudio(long chatId, byte[] content, String fileName, String title, String performer) {
        SendAudio audio = SendAudio.builder()
                .chatId(Long.toString(chatId))
                .audio(new InputFile(new ByteArrayInputStream(content), fileName))
                .title(title)
                .performer(performer)
                .build();

        try {
            telegramClient.execute(audio);
        } catch (TelegramApiException exception) {
            log.warn("Audio faylni yuborib bo‘lmadi. chatId={}", chatId, exception);
        }
    }

    public void sendVideo(long chatId, byte[] content, String fileName) {
        SendVideo video = SendVideo.builder()
                .chatId(Long.toString(chatId))
                .video(new InputFile(new ByteArrayInputStream(content), fileName))
                .supportsStreaming(true)
                .build();

        try {
            telegramClient.execute(video);
        } catch (TelegramApiException exception) {
            log.warn("Video faylni yuborib bo‘lmadi. chatId={}", chatId, exception);
        }
    }

    public void sendYoutubeDownloadOptions(long chatId, String videoId) {
        InlineKeyboardButton audioButton = InlineKeyboardButton.builder().text("🎵 Audio (mp3)").callbackData("yta:" + videoId).build();
        InlineKeyboardButton videoButton = InlineKeyboardButton.builder().text("🎬 Video").callbackData("ytv:" + videoId).build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(audioButton, videoButton)).build();

        SendMessage message = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text("Nimani yuklab olmoqchisiz?")
                .replyMarkup(markup)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.error("Tugmali xabarni yuborib bo‘lmadi. chatId={}", chatId, exception);
        }
    }

    public void answerCallbackQuery(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException exception) {
            log.debug("Callback query'ga javob berib bo‘lmadi. id={}", callbackQueryId);
        }
    }

    public void deleteMessage(long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }
        try {
            telegramClient.execute(DeleteMessage.builder().chatId(Long.toString(chatId)).messageId(messageId).build());
        } catch (TelegramApiException exception) {
            log.debug("Telegram xabarini o‘chirib bo‘lmadi. chatId={}, messageId={}", chatId, messageId);
        }
    }
}
