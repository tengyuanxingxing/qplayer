package io.github.timer_err.qml4j.android;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class VsyncFramePumpTest {
    static final class Pulses implements VsyncFramePump.Scheduler {
        final Queue<Runnable> callbacks = new ArrayDeque<>();
        public void post(Runnable r) { callbacks.add(r); }
        public void cancel(Runnable r) { callbacks.remove(r); }
        void pulse() { int count=callbacks.size();for(int i=0;i<count;i++)callbacks.remove().run(); }
    }
    @Test public void wakesCoalesceAndDrawsFollowDisplayPulsesAtAllRefreshRates() {
        for(int hz:new int[]{60,90,120,144,165}) {
            Pulses clock=new Pulses();AtomicInteger frames=new AtomicInteger();
            VsyncFramePump pump=new VsyncFramePump(clock,frames::incrementAndGet);
            for(int i=0;i<100;i++)pump.wake();
            assertEquals(1,clock.callbacks.size());assertEquals(0,frames.get());
            for(int i=0;i<hz;i++){clock.pulse();pump.rendered(pump.beginFrame(),true);}
            assertEquals(hz,frames.get());assertEquals(1,clock.callbacks.size());
            pump.rendered(pump.beginFrame(),false);clock.pulse();
            assertTrue(clock.callbacks.isEmpty());assertEquals(hz,frames.get());
        }
    }
    @Test public void inputArrivingDuringLastFrameCannotBeLost() {
        Pulses clock=new Pulses();AtomicInteger frames=new AtomicInteger();
        VsyncFramePump pump=new VsyncFramePump(clock,frames::incrementAndGet);
        pump.wake();clock.pulse();long before=pump.beginFrame();pump.wake();pump.rendered(before,false);
        clock.pulse();assertEquals(2,frames.get());
        pump.rendered(pump.beginFrame(),false);clock.pulse();assertEquals(2,frames.get());
    }
    @Test public void pauseAndDisposalStopCallbacksAndResumeDoesNotDuplicateThem() {
        Pulses clock=new Pulses();AtomicInteger frames=new AtomicInteger();
        VsyncFramePump pump=new VsyncFramePump(clock,frames::incrementAndGet);
        pump.wake();pump.pause();pump.wake();clock.pulse();assertEquals(0,frames.get());
        pump.resume();pump.resume();clock.pulse();assertEquals(1,frames.get());
        pump.dispose();pump.wake();pump.resume();clock.pulse();assertEquals(1,frames.get());
    }
}
