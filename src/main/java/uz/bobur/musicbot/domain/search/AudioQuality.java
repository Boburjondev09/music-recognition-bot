package uz.bobur.musicbot.domain.search;

public enum AudioQuality {

    AUTO("Auto", "5"),
    KBPS_128("128k", "128K"),
    KBPS_192("192k", "192K"),
    KBPS_256("256k", "256K"),
    KBPS_320("320k", "320K");

    private final String label;
    private final String ytDlpValue;

    AudioQuality(String label, String ytDlpValue) {
        this.label = label;
        this.ytDlpValue = ytDlpValue;
    }

    public String label() {
        return label;
    }

    public String ytDlpValue() {
        return ytDlpValue;
    }

    public AudioQuality next() {
        AudioQuality[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static AudioQuality fromKbps(int kbps) {
        return switch (kbps) {
            case 128 -> KBPS_128;
            case 192 -> KBPS_192;
            case 256 -> KBPS_256;
            case 320 -> KBPS_320;
            default -> throw new IllegalArgumentException(
                    "Qo‘llab-quvvatlanmaydigan audio bitrate: " + kbps
            );
        };
    }
}