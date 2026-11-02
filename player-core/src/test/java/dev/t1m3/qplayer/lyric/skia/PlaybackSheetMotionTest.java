package dev.t1m3.qplayer.lyric.skia;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackSheetMotionTest {
 @Test public void draggedPositionIsExactAndDoesNotApplyAnEasingCurve(){PlaybackSheetMotion m=new PlaybackSheetMotion();for(float y:new float[]{0f,.05f,.2f,.7f,.12f}){m.dragTo(y);assertEquals(y,m.offset(),0.00001f);}}
 @Test public void distanceVelocityAndReversalDecideRelease(){assertFalse(PlaybackSheetMotion.shouldDismiss(.12,0));assertTrue(PlaybackSheetMotion.shouldDismiss(.3,0));assertTrue(PlaybackSheetMotion.shouldDismiss(.3,-.05));assertTrue(PlaybackSheetMotion.shouldDismiss(.08,1.2));assertFalse(PlaybackSheetMotion.shouldDismiss(.01,3));assertFalse(PlaybackSheetMotion.shouldDismiss(.4,-.5));}
 @Test public void cancelReturnsToOpenAndCommitContinuesDownWithoutAPositionJump(){PlaybackSheetMotion m=new PlaybackSheetMotion();m.dragTo(.2f);m.releaseAt(.2f,0);assertEquals(.2f,m.offset(),0);for(int i=0;i<90;i++)m.advance(true,1f/120);assertEquals(0,m.offset(),.0001);m.releaseAt(.4f,1);float next=m.advance(false,1f/120);assertTrue(next>=.4f&&next<.43f);for(int i=0;i<120;i++)m.advance(false,1f/120);assertEquals(1,m.offset(),.0001);}
 @Test public void springDurationIsIndependentOfDisplayRefreshRate(){PlaybackSheetMotion a=new PlaybackSheetMotion(),b=new PlaybackSheetMotion();a.releaseAt(.4f,0);b.releaseAt(.4f,0);for(int i=0;i<30;i++)a.advance(true,1f/60);for(int i=0;i<60;i++)b.advance(true,1f/120);assertEquals(a.offset(),b.offset(),.0001);}
}
