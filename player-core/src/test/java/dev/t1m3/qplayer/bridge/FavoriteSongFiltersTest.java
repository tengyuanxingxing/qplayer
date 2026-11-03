package dev.t1m3.qplayer.bridge;

import dev.t1m3.qplayer.media.MediaRef;
import dev.t1m3.qplayer.media.Song;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FavoriteSongFiltersTest {
    private static Song song(String id, String title, String artist, String genre, String date) {
        Song song = new Song(); song.id = id; song.title = song.name = title;
        song.artist = artist; song.genre = genre;
        if (date != null) song.publishTimeMs = Instant.parse(date + "T00:00:00Z").toEpochMilli();
        return song;
    }
    @Test public void actualCollectionBuildsCountedStableFacetsWithoutInventingUnknownMetadata() {
        Song a = song("qq:song:a", "Rap English 1990", "A / B", "", null);
        a.artists = Arrays.asList(new MediaRef("a", "A"), new MediaRef("b", "B"), new MediaRef("a", "A"));
        Song b = song("netease:song:b", "二", "A", "Hip Hop", "2024-01-01");
        List<Map<String,Object>> groups = FavoriteSongFilters.groups(Arrays.asList(a,b,null));
        assertEquals(4,groups.size());
        assertEquals("artist",groups.get(0).get("key"));
        List<Map<String,Object>> artists = options(groups.get(0));
        assertEquals("A", artists.get(0).get("value"));
        assertEquals(2, artists.get(0).get("count"));
        assertEquals(1, artists.get(1).get("count"));
        assertEquals(1,options(groups.get(1)).size());
        assertEquals("2020", options(groups.get(2)).get(0).get("value"));
        for (Map<String,Object> group:groups) assertFalse(Arrays.asList("language","likedYear").contains(group.get("key")));
        assertTrue(FavoriteSongFilters.groups(Collections.emptyList()).isEmpty());
    }
    @Test public void combinationSearchMatchesAllTokensAcrossFieldsAndKeepsIdentityAndOrder() {
        Song a = song("qq:song:a", "Sky Live", "WatchMe", "Rap", "2023-01-01");
        a.album = new MediaRef("qq:album:a", "Heat Room");
        Song b = song("netease:song:b", "Sky Live", "WatchMe", "Rap", "2023-01-01");
        Song c = song("qq:song:c", "Sky", "WatchMe", "Pop", "2015-01-01");
        List<Song> all = Arrays.asList(a,b,c);
        List<Song> selected = FavoriteSongFilters.filter(all,"  SKY  ROOM ","WatchMe","Rap","2020","qq");
        assertEquals(1,selected.size()); assertSame(a,selected.get(0));
        assertEquals(Arrays.asList(a,b),FavoriteSongFilters.filter(all,"sky live","","Rap","2020",""));
        assertTrue(FavoriteSongFilters.filter(all,"不存在","","","","").isEmpty());
        assertEquals(all,FavoriteSongFilters.filter(all,"","","","",""));
    }
    @Test public void flatArtistMetadataPreservesNamesWithCommasAndSlashes() {
        Song a = song("qq:song:a","一","AC/DC / A, B","",null);
        a.artistNamesCsv="AC/DC\u0001A, B";
        assertSame(a, FavoriteSongFilters.filter(Collections.singletonList(a),"","AC/DC","","","").get(0));
        assertSame(a, FavoriteSongFilters.filter(Collections.singletonList(a),"","A, B","","","").get(0));
        a.artistNamesCsv="";
        assertSame(a, FavoriteSongFilters.filter(Collections.singletonList(a),"","AC/DC","","","").get(0));
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> options(Map<String,Object> group) {
        return (List<Map<String,Object>>)group.get("options");
    }
}
