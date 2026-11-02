package io.github.timer_err.qml4j.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.os.PowerManager;
import android.view.SurfaceView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=27, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class HighRefreshControllerTest {
    @Test public void actualWindowVoteRestoresAfterResumeAndReleasesForPowerSave() {
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            Activity app=activity.get();SurfaceView host=new SurfaceView(app);app.setContentView(host);
            activity.windowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idle();
            HighRefreshController refresh=new HighRefreshController(host);refresh.resume();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(host.hasWindowFocus());assertTrue(refresh.requestedHz>=60f);
            assertEquals(refresh.requestedHz,app.getWindow().getAttributes().preferredRefreshRate,0.01f);
            PowerManager power=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
            Shadows.shadowOf(power).setIsPowerSaveMode(true);refresh.refresh();
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0f,refresh.requestedHz,0f);
            Shadows.shadowOf(power).setIsPowerSaveMode(false);refresh.pause();
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0f,app.getWindow().getAttributes().preferredRefreshRate,0f);
            refresh.resume();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(refresh.requestedHz>=60f);
            refresh.dispose();assertEquals(0f,app.getWindow().getAttributes().preferredRefreshRate,0f);
        } finally { activity.pause().stop().destroy(); }
    }
}
