package io.github.timer_err.qml4j.android;

/** One render request per display pulse, with no lost wake at the idle boundary. */
final class VsyncFramePump {
    interface Scheduler { void post(Runnable frame); void cancel(Runnable frame); }
    private final Scheduler scheduler;
    private final Runnable draw;
    private final Runnable callback = this::onPulse;
    private boolean active, pending, paused, disposed;
    private long generation;

    VsyncFramePump(Scheduler scheduler, Runnable draw) { this.scheduler = scheduler; this.draw = draw; }

    synchronized void wake() {
        if (disposed) return;
        generation++;
        active = true;
        schedule();
    }
    synchronized long beginFrame() { return generation; }
    synchronized void rendered(long startedGeneration, boolean keepRendering) {
        if (disposed) return;
        // Input arriving after beginFrame must survive a frame deciding to sleep.
        active = keepRendering || generation != startedGeneration;
        if (active) schedule();
    }
    synchronized void pause() {
        paused = true;
        if (pending) scheduler.cancel(callback);
        pending = false;
    }
    synchronized void resume() { paused = false; wake(); }
    synchronized void dispose() { pause(); disposed = true; active = false; }
    private void schedule() {
        if (active && !paused && !disposed && !pending) {
            pending = true;
            scheduler.post(callback);
        }
    }
    private synchronized void onPulse() {
        pending = false;
        if (!active || paused || disposed) return;
        draw.run();
        schedule();
    }
}
