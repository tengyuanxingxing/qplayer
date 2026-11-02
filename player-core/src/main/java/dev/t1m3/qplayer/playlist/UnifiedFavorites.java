package dev.t1m3.qplayer.playlist;

import com.google.gson.Gson;
import dev.t1m3.qplayer.media.AccountProfile;
import dev.t1m3.qplayer.media.Song;
import dev.t1m3.qplayer.store.AppDirs;
import dev.t1m3.qplayer.store.StorageFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Account-scoped snapshots. Failed requests never turn a cached collection into an empty one. */
public final class UnifiedFavorites {
    public static final String ID = "qplayer:playlist:unified-favorites";
    public static final int BATCH_SIZE = 100;
    private final Path file;
    private final Gson gson = new Gson();
    private final Map<String, List<Song>> snapshots = new LinkedHashMap<>();
    public UnifiedFavorites() { this(AppDirs.indexFile("unified-favorites-v1.json")); }
    public UnifiedFavorites(Path file) { this.file = file; }
    private boolean loaded;

    public static final class Result {
        public final List<Song> songs;
        public Set<String> likedIds;
        public final boolean stale;
        public final boolean signedIn;
        public Result(List<Song> songs, boolean stale, boolean signedIn) {
            this.songs = songs; this.stale = stale; this.signedIn = signedIn;
        }
    }

    private synchronized List<Song> cached(String key) {
        if (!loaded) {
            loaded = true;
            if (Files.isRegularFile(file)) try {
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, List<Song>>>() {}.getType();
                Map<String, List<Song>> stored = gson.fromJson(StorageFiles.readUtf8(file), type);
                if (stored != null) snapshots.putAll(stored);
            } catch (Exception ignored) { /* A corrupt snapshot must not block a live sync. */ }
        }
        List<Song> rows = snapshots.get(key);
        return rows == null ? Collections.emptyList() : new ArrayList<>(rows);
    }
    private synchronized void save(String key, List<Song> rows) {
        snapshots.put(key, new ArrayList<>(rows));
        try { StorageFiles.writeUtf8Atomic(file, gson.toJson(snapshots)); }
        catch (Exception ignored) { /* Live results are still usable without disk cache. */ }
    }

    public CompletableFuture<Result> load(String provider, AccountProfile account,
            Function<String, CompletableFuture<Set<String>>> likes,
            Function<List<String>, CompletableFuture<List<Song>>> details) {
        if (account == null || !account.loggedIn || account.id == null || account.id.isEmpty())
            return CompletableFuture.completedFuture(new Result(Collections.emptyList(), false, false));
        String key = provider + "\n" + account.id;
        List<Song> old = cached(key);
        return likes.apply(provider).thenCompose(ids -> {
            List<String> ordered = new ArrayList<>(ids);
            Map<String, Song> resolved = new LinkedHashMap<>();
            for (Song song : old) if (ids.contains(song.id)) resolved.put(song.id, song);
            boolean[] partial = {false};
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int i = 0; i < ordered.size(); i += BATCH_SIZE) {
                final List<String> batch = new ArrayList<>(ordered.subList(i, Math.min(i + BATCH_SIZE, ordered.size())));
                chain = chain.thenCompose(ignored -> details.apply(batch).handle((songs, error) -> {
                    if (error != null || songs == null) partial[0] = true;
                    else for (Song song : songs) if (batch.contains(song.id)) resolved.put(song.id, song);
                    return null;
                }));
            }
            return chain.thenApply(ignored -> {
                List<Song> rows = new ArrayList<>();
                for (String id : ordered) {
                    Song song = resolved.get(id);
                    if (song != null) rows.add(song); else partial[0] = true;
                }
                // Confirmed removals are applied even if a metadata batch failed.
                save(key, rows);
                Result result = new Result(rows, partial[0], true);
                result.likedIds = new LinkedHashSet<>(ids);
                return result;
            });
        }).exceptionally(error -> new Result(old, true, true));
    }

    private static Song copy(Song s) {
        Song r = new Song();
        r.id=s.id; r.title=s.title; r.name=s.name; r.artists=s.artists; r.album=s.album;
        r.durationMs=s.durationMs; r.artworkUrl=s.artworkUrl; r.coverUrl=s.coverUrl;
        r.coverThumbPath=s.coverThumbPath; r.artist=s.artist; r.artistMediaId=s.artistMediaId;
        r.artistIdsCsv=s.artistIdsCsv; r.artistNamesCsv=s.artistNamesCsv; r.isrc=s.isrc;
        r.playable=s.playable; r.trial=s.trial; r.restricted=s.restricted;
        r.cachedOffline=s.cachedOffline; r.favoriteSources=s.favoriteSources;
        r.publishTimeMs=s.publishTimeMs; r.credits=s.credits; r.genre=s.genre;
        return r;
    }

    private static String normalized(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
    private static String artistKey(String text) {
        List<String> names = new ArrayList<>();
        for (String name : (text == null ? "" : text).split("[/&,、]")) names.add(normalized(name));
        Collections.sort(names); return String.join("|", names);
    }
    private static boolean sameRecording(Song a, Song b) {
        if (a.id.equals(b.id)) return true;
        if (normalized(a.title).isEmpty() || artistKey(a.artist).isEmpty()) return false;
        if (!normalized(a.title).equals(normalized(b.title)) || !artistKey(a.artist).equals(artistKey(b.artist))) return false;
        if (a.isrc != null && b.isrc != null && !a.isrc.isEmpty() && !b.isrc.isEmpty())
            return a.isrc.equalsIgnoreCase(b.isrc);
        return a.durationMs > 0 && b.durationMs > 0 && Math.abs(a.durationMs - b.durationMs) <= 2500L;
    }
    private static int rank(Song song, String preferred) {
        return (song.playable && !song.restricted && !song.trial ? 10 : 0)
                + (song.id.startsWith(preferred + ":") ? 1 : 0);
    }
    public static List<Song> merge(Collection<List<Song>> sources) { return merge(sources, "qq"); }
    /** One queue entry per recording. Keep source snapshots intact for platform filters. */
    public static List<Song> merge(Collection<List<Song>> sources, String preferred) {
        Map<String, List<Song>> groups = new LinkedHashMap<>();
        for (List<Song> rows : sources) for (Song song : rows) {
            String key = normalized(song.title) + "|" + artistKey(song.artist);
            List<Song> versions = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
            int match = -1;
            for (int i=0; i<versions.size(); i++) if (sameRecording(versions.get(i), song)) { match=i; break; }
            if (match < 0) { versions.add(copy(song)); continue; }
            Song prior = versions.get(match);
            String labels = prior.favoriteSources;
            if (song.favoriteSources != null && !song.favoriteSources.isEmpty() && !labels.contains(song.favoriteSources))
                labels += (labels.isEmpty() ? "" : " / ") + song.favoriteSources;
            if (rank(song, preferred) > rank(prior, preferred)) {
                prior = copy(song); versions.set(match, prior);
            }
            prior.favoriteSources = labels;
        }
        List<Song> result = new ArrayList<>();
        for (List<Song> versions : groups.values()) result.addAll(versions);
        return result;
    }
}
