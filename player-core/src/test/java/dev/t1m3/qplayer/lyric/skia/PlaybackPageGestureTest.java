package dev.t1m3.qplayer.lyric.skia;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackPageGestureTest {
    @Test public void horizontalSwipeLocksAndCannotTurnIntoAScrollOrTap() {
        PlaybackPageGesture g=new PlaybackPageGesture(); g.begin(200,200);
        assertEquals(PlaybackPageGesture.HORIZONTAL,g.move(170,202));
        assertEquals(PlaybackPageGesture.HORIZONTAL,g.move(100,300));
        assertEquals(1,g.finish(100,300));
        g.begin(100,200); assertEquals(-1,g.finish(200,203));
    }
    @Test public void verticalDragAndShortAbortedSwipeNeverSeek() {
        PlaybackPageGesture g=new PlaybackPageGesture(); g.begin(100,100);
        assertEquals(PlaybackPageGesture.VERTICAL,g.move(102,130));
        assertEquals(0,g.finish(200,140));
        g.begin(100,100); assertEquals(0,g.finish(120,100));
        assertEquals(PlaybackPageGesture.HORIZONTAL,g.direction());
        g.begin(100,100); assertEquals(0,g.finish(103,104)); assertEquals(PlaybackPageGesture.TAP,g.direction());
    }
    @Test public void onlyPaintedTextSeeksIncludingTheAnimatedScale() {
        LyricHitRegions regions=new LyricHitRegions();
        regions.add(30,100,150,125,30,100,1.2f,5000L);
        assertEquals(5000L,regions.timeAt(160,120));
        assertEquals(-1L,regions.timeAt(190,120));
        assertEquals(-1L,regions.timeAt(100,145));
        regions.clear(); assertEquals(-1L,regions.timeAt(100,120));
    }
}
