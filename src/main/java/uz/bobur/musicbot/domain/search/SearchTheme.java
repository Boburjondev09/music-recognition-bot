package uz.bobur.musicbot.domain.search;

public enum SearchTheme {
    DEFAULT("Default", "⬅️", "➡️", "❌", "🎨"),
    MINIMAL("Minimal", "‹", "›", "✕", "⚙"),
    PIXEL("Pixel", "◀", "▶", "■", "🎛"),
    MUSIC("Music", "⏪", "⏩", "⏹", "🎵");

    private final String label;
    private final String previous;
    private final String next;
    private final String close;
    private final String settings;

    SearchTheme(String label, String previous, String next, String close, String settings) {
        this.label = label;
        this.previous = previous;
        this.next = next;
        this.close = close;
        this.settings = settings;
    }

    public String label() {
        return label;
    }

    public String previous() {
        return previous;
    }

    public String next() {
        return next;
    }

    public String close() {
        return close;
    }

    public String settings() {
        return settings;
    }
}