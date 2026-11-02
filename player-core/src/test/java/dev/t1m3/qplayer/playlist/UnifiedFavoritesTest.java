package dev.t1m3.qplayer.playlist;

import dev.t1m3.qplayer.media.AccountProfile;
import dev.t1m3.qplayer.media.Song;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnifiedFavoritesTest {
    private AccountProfile account(String id) {
        AccountProfile a = new AccountProfile(); a.id = id; a.loggedIn = true; return a;
    }
    private List<Song> songs(List<String> ids) {
        List<Song> rows = new ArrayList<>();
        for (String id : ids) { Song s = new Song(); s.id=id; s.name=s.title=id; rows.add(s); }
        return rows;
    }
    private <T> CompletableFuture<T> failed() {
        CompletableFuture<T> f = new CompletableFuture<>(); f.completeExceptionally(new RuntimeException("offline")); return f;
    }
    @Test public void loadsEveryOneOf1016SongsAndRemovesConfirmedUnlikes() throws Exception {
        UnifiedFavorites store = new UnifiedFavorites(Files.createTempDirectory("favorites-test").resolve("likes.json"));
        Set<String> ids = new LinkedHashSet<>();
        for (int i=0; i<1016; i++) ids.add("qq:song:"+i);
        AtomicInteger batches = new AtomicInteger();
        UnifiedFavorites.Result result = store.load("qq", account("a"), p -> CompletableFuture.completedFuture(ids), batch -> {
            assertTrue(batch.size() <= 100); batches.incrementAndGet();
            return CompletableFuture.completedFuture(songs(batch));
        }).get();
        assertEquals(1016, result.songs.size()); assertEquals(11, batches.get()); assertFalse(result.stale);
        Set<String> reduced = new LinkedHashSet<>(ids); reduced.remove("qq:song:10");
        result = store.load("qq", account("a"), p -> CompletableFuture.completedFuture(reduced), batch -> failed()).get();
        assertEquals(1015, result.songs.size()); assertTrue(result.stale);
        assertFalse(result.songs.stream().anyMatch(s -> s.id.equals("qq:song:10")));
    }
    @Test public void offlineSnapshotSurvivesRestartAndNeverLeaksAcrossAccounts() throws Exception {
        java.nio.file.Path file = Files.createTempDirectory("favorites-test").resolve("likes.json");
        new UnifiedFavorites(file).load("qq", account("a"), p -> CompletableFuture.completedFuture(
                new LinkedHashSet<>(Arrays.asList("qq:song:1"))), batch -> CompletableFuture.completedFuture(songs(batch))).get();
        UnifiedFavorites restarted = new UnifiedFavorites(file);
        assertEquals(1, restarted.load("qq", account("a"), p -> failed(), batch -> failed()).get().songs.size());
        assertTrue(restarted.load("qq", account("b"), p -> failed(), batch -> failed()).get().songs.isEmpty());
        AccountProfile signedOut = account("a"); signedOut.loggedIn=false;
        assertFalse(restarted.load("qq", signedOut, p -> failed(), batch -> failed()).get().signedIn);
    }
    @Test public void mergesOnlyConfirmedSameRecordingAndKeepsSourceSnapshotsIndependent() {
        Song a=songs(Arrays.asList("qq:song:1")).get(0); a.isrc="ABC"; a.title="Song"; a.artist="Artist"; a.favoriteSources="QQ";
        Song b=songs(Arrays.asList("netease:song:2")).get(0); b.isrc="ABC"; b.title="Song"; b.artist="Artist"; b.favoriteSources="网易云";
        List<Song> rows=UnifiedFavorites.merge(Arrays.asList(Arrays.asList(a),Arrays.asList(b)));
        assertEquals(1, rows.size()); assertEquals("QQ / 网易云", rows.get(0).favoriteSources);
        assertEquals("QQ",a.favoriteSources);
        b.title="Song (Live)";
        assertEquals(2,UnifiedFavorites.merge(Arrays.asList(Arrays.asList(a),Arrays.asList(b))).size());
        a.isrc=""; b.title="Song"; b.artist="Artist";
        assertEquals(2,UnifiedFavorites.merge(Arrays.asList(Arrays.asList(a),Arrays.asList(b))).size());
    }    @Test public void metadataDuplicatesChooseExactlyOnePreferredPlayableSource() {
        Song a=songs(Arrays.asList("qq:song:1")).get(0); a.title="同 一 首"; a.artist="歌手"; a.durationMs=180000;
        Song b=songs(Arrays.asList("netease:song:2")).get(0); b.title="同一首"; b.artist="歌手"; b.durationMs=181000;
        List<List<Song>> sources=Arrays.asList(Arrays.asList(a),Arrays.asList(b));
        assertEquals(1,UnifiedFavorites.merge(sources,"qq").size());
        assertEquals(a.id,UnifiedFavorites.merge(sources,"qq").get(0).id);
        assertEquals(b.id,UnifiedFavorites.merge(sources,"netease").get(0).id);
        b.trial=true; assertEquals(a.id,UnifiedFavorites.merge(sources,"netease").get(0).id);
        b.title="同一首 (Live)"; assertEquals(2,UnifiedFavorites.merge(sources,"qq").size());
    }

}
