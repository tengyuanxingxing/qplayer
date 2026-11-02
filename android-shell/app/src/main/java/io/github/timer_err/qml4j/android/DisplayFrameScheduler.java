package io.github.timer_err.qml4j.android;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;

/** Choreographer belongs to the UI thread; QML/Skia still render on their GL thread. */
final class DisplayFrameScheduler implements VsyncFramePump.Scheduler {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.Map<Runnable, Choreographer.FrameCallback> callbacks = new java.util.HashMap<>();
    private final java.util.Set<Runnable> pending = new java.util.HashSet<>();
    volatile long frameTimeNanos;

    @Override public void post(Runnable frame) {
        if (Looper.myLooper() == main.getLooper()) postOnUi(frame);
        else main.post(() -> postOnUi(frame));
    }
    private void postOnUi(Runnable frame) {
        if (!pending.add(frame)) return;
        Choreographer.FrameCallback callback = callbacks.get(frame);
        if (callback == null) {
            callback = time -> {
                pending.remove(frame);
                frameTimeNanos = time;
                frame.run();
            };
            callbacks.put(frame, callback);
        }
        Choreographer.getInstance().postFrameCallback(callback);
    }
    @Override public void cancel(Runnable frame) {
        main.post(() -> {
            if (pending.remove(frame)) Choreographer.getInstance().removeFrameCallback(callbacks.get(frame));
        });
    }
}
