package dev.t1m3.qplayer.bridge;

import dev.t1m3.qplayer.media.MediaRef;
import dev.t1m3.qplayer.media.Song;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/** Local collection facets derived only from supplied metadata, never guessed from titles. */
public final class FavoriteSongFilters {
    private FavoriteSongFilters() { }

    public static List<Map<String, Object>> groups(List<Song> songs) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (String key : Arrays.asList("artist", "genre", "decade", "provider")) {
            Map<String, Integer> counts = new HashMap<>();
            if (songs != null) for (Song song : songs) {
                if (song == null) continue;
                Set<String> values = values(song, key);
                for (String value : values) counts.put(value, counts.getOrDefault(value, 0) + 1);
            }
            if (counts.isEmpty()) continue;
            List<String> ordered = new ArrayList<>(counts.keySet());
            ordered.sort((a, b) -> {
                int comparison = Integer.compare(counts.get(b), counts.get(a));
                return comparison != 0 ? comparison : a.compareTo(b);
            });
            List<Map<String, Object>> options = new ArrayList<>();
            for (String value : ordered) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("value", value);
                option.put("label", label(key, value));
                option.put("count", counts.get(value));
                options.add(option);
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("key", key);
            group.put("title", "artist".equals(key) ? "歌手" : "genre".equals(key) ? "曲风" : "decade".equals(key) ? "发行年代" : "播放来源");
            group.put("options", options);
            groups.add(group);
        }
        return groups;
    }

    public static List<Song> filter(List<Song> songs, String query, String artist,
                                    String genre, String decade, String provider) {
        List<Song> result = new ArrayList<>();
        String normalizedQuery = safe(query).toLowerCase(Locale.ROOT);
        String[] tokens = normalizedQuery.isEmpty() ? new String[0] : normalizedQuery.split("\\s+");
        if (songs == null) return result;
        for (Song song : songs) {
            if (song == null || !matches(song, "artist", artist) || !matches(song, "genre", genre)
                    || !matches(song, "decade", decade) || !matches(song, "provider", provider)) continue;
            String haystack = (safe(song.name) + " " + safe(song.title) + " " + safe(song.artist)
                    + " " + String.join(" ", artists(song)) + " " + (song.album == null ? "" : safe(song.album.name)))
                    .toLowerCase(Locale.ROOT);
            boolean found = true;
            for (String token : tokens) if (!haystack.contains(token)) { found = false; break; }
            if (found) result.add(song);
        }
        return result;
    }

    private static boolean matches(Song song, String key, String selection) {
        return safe(selection).isEmpty() || values(song, key).contains(selection);
    }

    private static Set<String> values(Song song, String key) {
        if ("artist".equals(key)) return artists(song);
        Set<String> result = new LinkedHashSet<>();
        if ("genre".equals(key)) {
            // Preserve the source's genre label as supplied; slash may belong to its name.
            add(result, song.genre);
        } else if ("decade".equals(key) && song.publishTimeMs > 0) {
            try {
                int year = Instant.ofEpochMilli(song.publishTimeMs).atZone(ZoneOffset.UTC).getYear();
                if (year >= 1800 && year <= 2200) result.add(String.valueOf(year / 10 * 10));
            } catch (RuntimeException ignored) { }
        } else if ("provider".equals(key)) {
            String id = safe(song.id);
            int separator = id.indexOf(':');
            if (separator > 0) add(result, id.substring(0, separator));
        }
        return result;
    }

    private static Set<String> artists(Song song) {
        Set<String> result = new LinkedHashSet<>();
        if (song.artists != null) for (MediaRef artist : song.artists) if (artist != null) add(result, artist.name);
        if (!result.isEmpty()) return result;
        if (!safe(song.artistNamesCsv).isEmpty()) {
            for (String artist : song.artistNamesCsv.split("\u0001")) add(result, artist);
        }
        if (result.isEmpty()) for (String artist : safe(song.artist).split("\\s+/\\s+|[;；]")) add(result, artist);
        return result;
    }

    private static String label(String key, String value) {
        if ("decade".equals(key)) return value + "年代";
        if ("provider".equals(key)) {
            if ("qq".equals(value)) return "QQ音乐";
            if ("netease".equals(value)) return "网易云音乐";
            if ("soda".equals(value)) return "汽水音乐";
        }
        return value;
    }
    private static void add(Set<String> result, String value) { if (!safe(value).isEmpty()) result.add(safe(value)); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
