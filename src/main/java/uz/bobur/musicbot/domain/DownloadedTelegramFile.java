package uz.bobur.musicbot.domain;

public record DownloadedTelegramFile(
        String filePath,
        String fileName,
        String contentType,
        byte[] content
) {
    public long size() {
        return content == null ? 0L : content.length;
    }
}
