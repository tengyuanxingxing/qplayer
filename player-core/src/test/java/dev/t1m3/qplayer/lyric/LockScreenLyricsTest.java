package dev.t1m3.qplayer.lyric;

import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LockScreenLyricsTest {
    private LyricLine line(String text, long ms) {
        LyricLine line = new LyricLine();
        line.syllables.add(new Syllable(text, ms, 1000));
        return line;
    }
    @Test public void publishesTimedChineseLyricsWithCanonicalIdentityAndOffset() {
        String encoded = LockScreenLyrics.encode("qq:song:mid", "歌曲", "歌手", "专辑",
                Arrays.asList(line("一点一滴", 254123), line("累积成", 260000)), 250);
        com.google.gson.JsonObject info = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals("qq:song:mid", info.get("songId").getAsString());
        assertEquals("歌曲", info.get("songName").getAsString());
        assertEquals("lrc", info.get("format").getAsString());
        assertEquals("[04:14.373]一点一滴\n[04:20.250]累积成\n", info.get("lyric").getAsString());
    }
    @Test public void emptyTrackClearsPayloadAndNegativeOffsetNeverProducesNegativeTime() {
        assertEquals("", LockScreenLyrics.encode("qq:song:new", "", "", "", Collections.emptyList(), 0));
        assertEquals("", LockScreenLyrics.encode("", "", "", "", Arrays.asList(line("旧歌词", 0)), 0));
        String encoded = LockScreenLyrics.encode("netease:song:1", "", "", "", Arrays.asList(line("歌词\n换行", 20)), -500);
        assertTrue(JsonParser.parseString(encoded).getAsJsonObject().get("lyric").getAsString().startsWith("[00:00.000]歌词 换行"));
    }
    @Test public void oversizePayloadCannotExhaustMediaSessionBinderTransaction() {
        String encoded = LockScreenLyrics.encode("qq:song:mid", "", "", "",
                Arrays.asList(line("正常", 0), line(new String(new char[60000]).replace('\0', 'a'), 1000)), 0);
        assertTrue(encoded.length() < 50000);
        assertTrue(encoded.contains("正常"));
    }
}
