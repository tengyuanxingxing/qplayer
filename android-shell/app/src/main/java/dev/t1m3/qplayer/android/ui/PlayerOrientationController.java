package dev.t1m3.qplayer.android.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.OrientationEventListener;

/** Owns rotation only while the player is visible. No pending turn survives pause/disable. */
final class PlayerOrientationController {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StablePlayerOrientation policy = new StablePlayerOrientation();
    private final OrientationEventListener sensor;
    private boolean resumed, wanted, systemAuto = true, observing;
    private final android.database.ContentObserver rotationObserver = new android.database.ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { readSystemAuto();sample(); }
    };
    private int angle = -1;
    private final Runnable settle = this::sample;
    PlayerOrientationController(Activity activity) {
        this.activity = activity;
        sensor = new OrientationEventListener(activity) {
            @Override public void onOrientationChanged(int value) {
                angle = value;
                sample();
            }
        };
        portrait();
    }
    void setWanted(boolean value) { if (wanted == value) return; wanted = value; refresh(); }
    void resume() { resumed = true; refresh(); }
    void pause() { resumed = false; refresh(); }
    private void refresh() {
        handler.removeCallbacks(settle); policy.reset(); angle = -1;
        if (wanted && resumed && sensor.canDetectOrientation()) {
            if (!observing) {
                activity.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                        Settings.System.ACCELEROMETER_ROTATION),false,rotationObserver);
                observing=true;
            }
            readSystemAuto();sensor.enable();
        } else {
            sensor.disable();
            if (observing) { activity.getContentResolver().unregisterContentObserver(rotationObserver);observing=false; }
            if (!wanted) { policy.portrait();portrait(); }
        }
    }
    private void sample() {
        if (!wanted || !resumed) return;
        if (!systemAuto) { policy.portrait();handler.removeCallbacks(settle);portrait();return; }
        long now=SystemClock.uptimeMillis();
        int result = policy.update(angle,now);
        if (activity.getRequestedOrientation() != result) activity.setRequestedOrientation(result);
        handler.removeCallbacks(settle);
        long remaining=policy.remaining(now);
        if (remaining>0) handler.postDelayed(settle,remaining);
    }
    private void readSystemAuto() {
        systemAuto=Settings.System.getInt(activity.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1)!=0;
    }
    private void portrait() {
        if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    void close() {
        sensor.disable();handler.removeCallbacks(settle);
        if (observing) { activity.getContentResolver().unregisterContentObserver(rotationObserver);observing=false; }
    }
}
