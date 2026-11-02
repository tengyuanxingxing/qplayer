package dev.t1m3.qplayer.qml;

import org.junit.Test;
import static org.junit.Assert.*;

public class PageFlickableTest {
    private PageFlickable pager() {
        PageFlickable pager = new PageFlickable();
        pager.width.set(390); pager.height.set(500); pager.contentWidth.set(780);
        return pager;
    }
    @Test public void releaseBeginsSnapImmediatelyAndFinishesWithin250ms() {
        PageFlickable pager = pager();
        pager.moving.set(true); pager.contentX.set(62f);
        pager.tick(System.nanoTime() + 500_000_000L);
        assertEquals("finger-owned offset is not tweened",62f,pager.contentX.peekFloat(),0.01f);
        long release=System.nanoTime(); pager.startFling(0,0);
        assertEquals(0,pager.pageIndex.peekInt());
        pager.tick(release+100_000_000L);
        assertTrue(pager.contentX.peekFloat()<20);
        pager.tick(release+240_000_000L);
        assertEquals(0f,pager.contentX.peekFloat(),0.01f);
        assertFalse(pager.moving.peek());
    }
    @Test public void distanceAndVelocityChooseAdjacentPageWithoutFreeInertia() {
        PageFlickable pager=pager();pager.contentX.set(160f);pager.startFling(0,0);
        assertEquals(1,pager.pageIndex.peekInt());pager.tick(System.nanoTime()+240_000_000L);
        assertEquals(390f,pager.contentX.peekFloat(),0.01f);
        pager.contentX.set(350f);pager.startFling(-900,0);
        assertEquals(0,pager.pageIndex.peekInt());pager.tick(System.nanoTime()+240_000_000L);
        assertEquals(0f,pager.contentX.peekFloat(),0.01f);
    }
    @Test public void cancelHideAndResizeNormalizeWithoutHalfPages() {
        PageFlickable pager=pager();pager.contentX.set(160f);pager.stopScroll();
        assertEquals(0f,pager.contentX.peekFloat(),0.01f);
        pager.pageIndex.set(1);pager.tick(System.nanoTime()+240_000_000L);
        pager.contentX.set(180f);pager.pageActive.set(false);pager.pageActive.set(true);
        assertEquals(390f,pager.contentX.peekFloat(),0.01f);
        pager.width.set(480);pager.contentWidth.set(960);
        assertEquals(480f,pager.contentX.peekFloat(),0.01f);
        assertFalse(pager.moving.peek());
    }
}
