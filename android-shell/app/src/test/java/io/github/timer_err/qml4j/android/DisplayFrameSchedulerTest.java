package io.github.timer_err.qml4j.android;

import android.app.Application;
import android.os.Looper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowChoreographer;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=27, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DisplayFrameSchedulerTest {
    @Test public void actualChoreographerSchedulesAndCancelsDisplayRequests() {
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(8));
        ShadowChoreographer.setPaused(true);
        DisplayFrameScheduler scheduler=new DisplayFrameScheduler();AtomicInteger frames=new AtomicInteger();
        VsyncFramePump pump=new VsyncFramePump(scheduler,frames::incrementAndGet);
        pump.wake();pump.wake();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));
        assertTrue(frames.get()>0);assertTrue(scheduler.frameTimeNanos>0);
        pump.pause();Shadows.shadowOf(Looper.getMainLooper()).idle();int paused=frames.get();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));assertEquals(paused,frames.get());
        pump.resume();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));assertTrue(frames.get()>paused);
        pump.dispose();Shadows.shadowOf(Looper.getMainLooper()).idle();int stopped=frames.get();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));assertEquals(stopped,frames.get());
    }
}
