package uz.bobur.musicbot.exception;

public class MusicSearchException extends MusicBotException {

    public MusicSearchException(String message) {
        super(message);
    }

    public MusicSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}