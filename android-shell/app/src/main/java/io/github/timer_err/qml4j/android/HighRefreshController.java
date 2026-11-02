package io.github.timer_err.qml4j.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

/** Window + Surface + modern View vote, reapplied on focus/resume/display changes. */
final class HighRefreshController implements DisplayManager.DisplayListener {
    private final SurfaceView host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable apply = this::applyOnUi;
    private boolean resumed = true, registered, disposed;
    private DisplayManager displays;
    volatile float requestedHz, displayHz;
    private final BroadcastReceiver powerChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    HighRefreshController(SurfaceView host) { this.host = host; }
    void resume() {
        resumed = true;
        if (!registered && host.isAttachedToWindow()) {
            displays = (DisplayManager)host.getContext().getSystemService(Context.DISPLAY_SERVICE);
            if (displays != null) displays.registerDisplayListener(this, main);
            IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33)
                host.getContext().registerReceiver(powerChanged,filter,Context.RECEIVER_NOT_EXPORTED);
            else host.getContext().registerReceiver(powerChanged,filter);
            registered = true;
        }
        refresh();
    }
    void pause() {
        resumed = false;
        if (registered) {
            if (displays != null) displays.unregisterDisplayListener(this);
            host.getContext().unregisterReceiver(powerChanged);
            registered = false;
        }
        refresh();
    }
    void dispose() { pause(); disposed = true; main.removeCallbacks(apply); applyOnUi(); }
    void refresh() { main.removeCallbacks(apply); main.post(apply); }
    @Override public void onDisplayAdded(int id) { refresh(); }
    @Override public void onDisplayRemoved(int id) { refresh(); }
    @Override public void onDisplayChanged(int id) { if (host.getDisplay() != null && id == host.getDisplay().getDisplayId()) refresh(); }

    private void applyOnUi() {
        try {
            Display display = host.getDisplay();
            if (display == null) return;
            displayHz = display.getRefreshRate();
            Display.Mode current = display.getMode();
            Display.Mode[] modes = display.getSupportedModes();
            int[] widths = new int[modes.length], heights = new int[modes.length];
            float[] rates = new float[modes.length];
            for (int i = 0; i < modes.length; i++) {
                widths[i] = modes[i].getPhysicalWidth(); heights[i] = modes[i].getPhysicalHeight(); rates[i] = modes[i].getRefreshRate();
            }
            PowerManager power = (PowerManager)host.getContext().getSystemService(Context.POWER_SERVICE);
            float rate = (!resumed || disposed || !host.hasWindowFocus()) ? 0f : RefreshRatePolicy.choose(
                    current.getPhysicalWidth(), current.getPhysicalHeight(), current.getRefreshRate(), widths, heights, rates,
                    Build.VERSION.SDK_INT >= 31 ? current.getAlternativeRefreshRates() : new float[0],
                    power != null && power.isPowerSaveMode());
            if (host.getContext() instanceof Activity) {
                Activity activity = (Activity)host.getContext();
                WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
                if (attrs.preferredRefreshRate != rate) {
                    attrs.preferredRefreshRate = rate;
                    activity.getWindow().setAttributes(attrs);
                }
            }
            Surface surface = host.getHolder().getSurface();
            if (Build.VERSION.SDK_INT >= 30 && surface != null && surface.isValid())
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            // Public ARR API exists on newer OS versions than our compile SDK.
            if (Build.VERSION.SDK_INT >= 35) ModernVote.apply(host, rate);
            if (rate != requestedHz) android.util.Log.i("qplayer.refresh", "requested=" + rate + "Hz display=" + displayHz + "Hz");
            requestedHz = rate;
        } catch (RuntimeException e) {
            android.util.Log.w("qplayer.refresh", "refresh hint rejected", e);
        }
    }

    private static final class ModernVote {
        private static final java.lang.reflect.Method METHOD = find();
        private static java.lang.reflect.Method find() {
            try { return View.class.getMethod("setRequestedFrameRate", float.class); }
            catch (ReflectiveOperationException e) { return null; }
        }
        static void apply(View view, float rate) {
            if (METHOD == null) return;
            try { METHOD.invoke(view, rate); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }
}
