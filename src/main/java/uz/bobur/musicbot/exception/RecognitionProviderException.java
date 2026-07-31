package uz.bobur.musicbot.exception;

public class RecognitionProviderException extends MusicBotException {

    public RecognitionProviderException(String message) {
        super(message);
    }

    public RecognitionProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
