package dev.t1m3.qplayer.lyric;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;

/** Timed lyric contract consumed by compatible OEM lock screens via MediaMetadata.lyricInfo.
 * No overlay permission, private platform API or per-line metadata churn is required. */
public final class LockScreenLyrics {
    public static final String METADATA_KEY = "lyricInfo";
    private static final int MAX_LRC_CHARS = 48_000;
    private LockScreenLyrics() {}

    public static String encode(String mediaId, String title, String artist, String album,
                                List<LyricLine> lines, long offsetMs) {
        if (mediaId == null || mediaId.isEmpty() || lines == null || lines.isEmpty()) return "";
        StringBuilder lrc = new StringBuilder();
        for (LyricLine line : lines) {
            if (line == null || line.syllables.isEmpty()) continue;
            String text = line.text().replace('\r', ' ').replace('\n', ' ').trim();
            if (text.isEmpty()) continue;
            long time = Math.max(0L, line.startMs() + offsetMs);
            String entry = String.format(Locale.ROOT, "[%02d:%02d.%03d]%s\n",
                    time / 60000, time / 1000 % 60, time % 1000, text);
            if (lrc.length() + entry.length() > MAX_LRC_CHARS) break;
            lrc.append(entry);
        }
        if (lrc.length() == 0) return "";
        JsonObject info = new JsonObject();
        info.addProperty("songName", bounded(title));
        info.addProperty("artist", bounded(artist));
        info.addProperty("album", bounded(album));
        info.addProperty("songId", bounded(mediaId));
        info.addProperty("format", "lrc");
        info.addProperty("lyric", lrc.toString());
        return info.toString();
    }

    private static String bounded(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 1024));
    }
}
