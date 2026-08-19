package uz.bobur.musicbot.domain.search;

public enum SearchSort {
    RELEVANCE("Relevance"),
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    DURATION_ASC("Duration ↑"),
    DURATION_DESC("Duration ↓");

    private final String label;

    SearchSort(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public SearchSort next() {
        SearchSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}