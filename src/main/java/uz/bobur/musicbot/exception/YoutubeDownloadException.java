package uz.bobur.musicbot.exception;

public class YoutubeDownloadException extends MusicBotException {

    public YoutubeDownloadException(String message) {
        super(message);
    }

    public YoutubeDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}