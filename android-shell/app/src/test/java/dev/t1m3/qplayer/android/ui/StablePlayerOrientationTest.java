package dev.t1m3.qplayer.android.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class StablePlayerOrientationTest {
    @Test public void landscapeNeedsAFullSecondAndJitterCancelsIt() {
        StablePlayerOrientation p = new StablePlayerOrientation();
        assertEquals(1,p.update(270,100));
        assertEquals(1,p.update(270,1099));
        assertEquals(0,p.update(270,1100));
        assertEquals(1,p.update(20,1200));
        assertEquals(1,p.update(90,1300));
        assertEquals(1,p.update(-1,2000));
        assertEquals(1,p.update(90,2300));
        assertEquals(8,p.update(90,3300));
    }
    @Test public void switchingLandscapeSidesKeepsOldSideUntilNewSideSettles() {
        StablePlayerOrientation p=new StablePlayerOrientation();p.update(270,0);assertEquals(0,p.update(270,1000));
        assertEquals(0,p.update(90,1100));assertEquals(0,p.update(90,2099));assertEquals(8,p.update(90,2100));
        assertEquals(8,p.update(-1,2200));assertEquals(8,p.update(130,2300));
        assertEquals(1,p.update(0,2400));
    }
    @Test public void pauseOrDisableDiscardsAnUnfinishedTurn() {
        StablePlayerOrientation p = new StablePlayerOrientation();
        p.update(90,100);p.reset();
        assertEquals(1,p.update(90,1500));
        assertEquals(8,p.update(90,2500));
    }
}
