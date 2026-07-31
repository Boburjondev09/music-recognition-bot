package uz.bobur.musicbot.domain;

public record TelegramUserContext(
        long userId,
        long chatId,
        String username,
        String firstName
) {
}
