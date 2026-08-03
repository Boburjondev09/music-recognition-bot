package uz.bobur.musicbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
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
