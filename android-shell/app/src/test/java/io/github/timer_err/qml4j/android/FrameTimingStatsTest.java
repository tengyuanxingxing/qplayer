package io.github.timer_err.qml4j.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameTimingStatsTest {
    @Test public void measuresRenderingInsteadOfAssumingScreenRefreshRate() {
        FrameTimingStats stats=new FrameTimingStats();long time=1_000_000_000L;stats.frame(time,2_000_000L);
        FrameTimingStats.Sample sample=null;
        for(int i=0;i<61;i++){time+=16_666_667L;FrameTimingStats.Sample s=stats.frame(time,3_000_000L);if(s!=null)sample=s;}
        assertNotNull(sample);assertEquals(60f,sample.fps,.01f);assertEquals(16.667f,sample.p95Ms,.01f);assertEquals(3f,sample.workMs,.01f);
    }
    @Test public void idlePauseDoesNotPolluteNextMeasurement() {
        FrameTimingStats stats=new FrameTimingStats();stats.frame(1_000_000_000L,1L);
        assertNull(stats.frame(12_000_000_000L,1L));
        FrameTimingStats.Sample sample=null;
        for(int i=1;i<=166;i++){FrameTimingStats.Sample s=stats.frame(12_000_000_000L+i*6_060_606L,1_000_000L);if(s!=null)sample=s;}
        assertNotNull(sample);assertEquals(165f,sample.fps,.01f);
    }
}
