package uz.bobur.musicbot.exception;

public class Mp3SourceException extends MusicBotException {

    public Mp3SourceException(String message) {
        super(message);
    }

    public Mp3SourceException(String message, Throwable cause) {
        super(message, cause);
    }
}