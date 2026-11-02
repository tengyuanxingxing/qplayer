package dev.t1m3.qplayer.media;
import java.util.Locale;
/** Classify explicit provider release types; never infer an album from track count. */
public final class AlbumCategory {
    private AlbumCategory() {}
    public static String of(String raw, String name) {
        String type = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
        if (type.contains("live") || type.contains("现场") || type.contains("演唱会")) return "live";
        if (type.equals("ep") || type.contains("迷你") || type.contains("extended play")) return "ep";
        if (type.contains("single") || type.contains("单曲")) return "single";
        if (type.contains("album") || type.contains("专辑")) return "album";
        String title = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (title.matches(".*(?:\\blive\\b|现场|演唱会).*")) return "live";
        if (title.matches(".*\\bep\\b.*")) return "ep";
        return "unknown";
    }
}
