package uz.bobur.musicbot.exception;

public class MusicBotException extends RuntimeException {

    public MusicBotException(String message) {
        super(message);
    }

    public MusicBotException(String message, Throwable cause) {
        super(message, cause);
    }
}
