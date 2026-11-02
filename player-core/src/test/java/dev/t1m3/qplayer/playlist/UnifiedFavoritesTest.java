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

    @Test public void recoversAll1018QqSongsWhenTheLargeMetadataRequestSilentlyReturnsEmpty() throws Exception {
        UnifiedFavorites store=new UnifiedFavorites(Files.createTempDirectory("favorites-batch").resolve("likes.json"));
        Set<String> ids=new LinkedHashSet<>();for(int i=0;i<1018;i++)ids.add("qq:song:"+i);
        AtomicInteger calls=new AtomicInteger();
        UnifiedFavorites.Result result=store.load("qq",account("a"),p->CompletableFuture.completedFuture(ids),batch->{
            calls.incrementAndGet();return CompletableFuture.completedFuture(batch.size()>10?Collections.emptyList():songs(batch));
        }).get();
        assertEquals(1018,result.songs.size());assertEquals(1018,result.likedIds.size());assertFalse(result.stale);
        assertEquals("qq:song:0",result.songs.get(0).id);assertEquals("qq:song:1017",result.songs.get(1017).id);
        assertTrue(calls.get()>11);
    }
    @Test public void stableMergeDoesNotGroupDifferentRecordingsAheadOfInterveningQqSongs() {
        Song a=songs(Arrays.asList("qq:song:1")).get(0);a.title="歌";a.artist="歌手";a.durationMs=100000;
        Song b=songs(Arrays.asList("qq:song:2")).get(0);b.title="其他";b.artist="歌手";
        Song c=songs(Arrays.asList("qq:song:3")).get(0);c.title="歌";c.artist="歌手";c.durationMs=200000;
        assertEquals(Arrays.asList(a.id,b.id,c.id),ids(UnifiedFavorites.merge(Arrays.asList(Arrays.asList(a,b,c)))));
    }
    @Test public void rapGroupsPreserveTheWholeQqTailEvenWhenNeteaseIsPreferredForDuplicates() {
        List<Song> qq=songs(Arrays.asList("qq:song:r1","qq:song:r2","qq:song:pop1","qq:song:pop2"));
        List<Song> ne=songs(Arrays.asList("netease:song:rap1","netease:song:pop-duplicate","netease:song:rap2"));
        ne.get(1).title=qq.get(2).title;ne.get(1).artist=qq.get(2).artist="歌手";ne.get(1).durationMs=qq.get(2).durationMs=180000;
        Map<String,List<Song>> sources=new LinkedHashMap<>();sources.put("qq",qq);sources.put("netease",ne);
        FavoritesMix.Config config=new FavoritesMix.Config();config.mode="blocks";config.baseEnd="qq:song:r2";
        List<Song> blocks=FavoritesMix.order(sources,"netease",config);
        assertEquals(Arrays.asList("qq:song:r1","qq:song:r2","netease:song:rap1","netease:song:rap2","netease:song:pop-duplicate","qq:song:pop2"),ids(blocks));
        config.mode="interleave";
        assertEquals(Arrays.asList("qq:song:r1","netease:song:rap1","qq:song:r2","netease:song:rap2","netease:song:pop-duplicate","qq:song:pop2"),ids(FavoritesMix.order(sources,"netease",config)));
        assertEquals(4,qq.size());assertEquals("qq:song:pop1",qq.get(2).id);
    }
    private List<String> ids(List<Song> songs){List<String> ids=new ArrayList<>();for(Song song:songs)ids.add(song.id);return ids;}

    @Test public void genericMixSupportsEitherPlatformAndIndependentRangesWithCustomRatios() {
        List<Song> a=songs(Arrays.asList("custom:song:before","custom:song:a","custom:song:b","custom:song:c","custom:song:after"));
        List<Song> b=songs(Arrays.asList("other:song:unselected","other:song:x","other:song:y"));
        Map<String,List<Song>> sources=new LinkedHashMap<>();sources.put("other",b);sources.put("custom",a);
        FavoritesMix.Config c=new FavoritesMix.Config();c.baseProvider="custom";c.insertProvider="other";c.baseStart=a.get(1).id;c.baseEnd=a.get(3).id;c.insertStart=b.get(1).id;c.mode="interleave";c.baseRatio=2;c.insertRatio=1;
        assertEquals(Arrays.asList(a.get(0).id,a.get(1).id,a.get(2).id,b.get(1).id,a.get(3).id,b.get(2).id,a.get(4).id,b.get(0).id),ids(FavoritesMix.order(sources,"custom",c)));
        c.baseEnd="custom:song:removed";assertFalse(FavoritesMix.valid(sources,c));
        assertEquals(Arrays.asList(a.get(0).id,a.get(1).id,a.get(2).id,a.get(3).id,a.get(4).id,b.get(0).id,b.get(1).id,b.get(2).id),ids(FavoritesMix.order(sources,"custom",c)));
    }

}
