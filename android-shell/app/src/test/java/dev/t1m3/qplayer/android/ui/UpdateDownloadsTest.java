package dev.t1m3.qplayer.android.ui;

import android.app.Application;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.net.Uri;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=27, application=Application.class)
public class UpdateDownloadsTest {
    private Context app;private SharedPreferences state;
    @Before public void setup() {app=RuntimeEnvironment.getApplication();state=UpdateDownloads.prefs(app);state.edit().clear().commit();UpdateDownloadService.cancelCurrent();UpdateDownloads.serviceStarted();}
    @Test public void duplicateTapsWhileServiceStartsReuseOneAppDownload() {
        String raw="https://github.com/tengyuanxingxing/qplayer/releases/download/v1.9.18-custom/QPlayer-1.9.18-custom-debug.apk";
        UpdateDownloads.start(app,new String[]{raw});
        assertEquals(0,UpdateDownloads.inspect(app).progress);
        UpdateDownloads.start(app,new String[]{raw});
        org.robolectric.shadows.ShadowApplication shadow=org.robolectric.Shadows.shadowOf((Application)app);
        assertNotNull(shadow.getNextStartedService());assertNull(shadow.getNextStartedService());
        UpdateDownloads.cancel(app);assertEquals(-1,UpdateDownloads.inspect(app).progress);
    }
    @Test public void legacyDetachedDownloadIsRemovedWithoutRestartingIt() {
        DownloadManager dm=(DownloadManager)app.getSystemService(Context.DOWNLOAD_SERVICE);
        long id=dm.enqueue(new DownloadManager.Request(Uri.parse("https://example.org/old.apk")));
        state.edit().putLong("id",id).putBoolean("running",true).commit();
        assertEquals(-1,UpdateDownloads.inspect(app).progress);
        try(android.database.Cursor cursor=dm.query(new DownloadManager.Query().setFilterById(id))) {assertEquals(0,cursor.getCount());}
        assertFalse(state.contains("id"));
    }
    @Test public void processRestartDoesNotResurrectAnOrphanedTransfer()throws Exception {
        File partial=target();new File(partial.getPath()+".part").createNewFile();
        state.edit().putBoolean("running",true).putString("path",partial.getAbsolutePath()).commit();
        assertEquals(-1,UpdateDownloads.inspect(app).progress);assertFalse(state.getBoolean("running",false));
        assertFalse(new File(partial.getPath()+".part").exists());
    }
    @Test public void taskRemovalCancelsStateAndNotificationAndServiceIsNotSticky()throws Exception {
        UpdateDownloadService service=Robolectric.buildService(UpdateDownloadService.class).create().get();
        File target=target();new File(target.getPath()+".part").createNewFile();
        state.edit().putBoolean("running",true).putString("path",target.getAbsolutePath()).commit();
        NotificationManager nm=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(UpdateDownloads.NOTIFICATION,new Notification.Builder(app,UpdateDownloads.CHANNEL).setContentTitle("download").setSmallIcon(android.R.drawable.stat_sys_download).build());
        service.onTaskRemoved(null);
        assertEquals(-1,UpdateDownloads.inspect(app).progress);assertEquals(0,nm.getActiveNotifications().length);
        assertFalse(new File(target.getPath()+".part").exists());
        assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(null,0,1));service.onDestroy();
    }
    @Test public void verifiedReadyPackageSurvivesReattachAndWrongVersionNeverInstalls()throws Exception {
        ShadowPackageManager pm=Shadow.extract(app.getPackageManager());pm.installPackage(info("1.9.17-custom",116));
        File file=target();file.createNewFile();
        state.edit().putString("version","1.9.18-custom").putString("path",file.getAbsolutePath()).putBoolean("ready",true).commit();
        pm.setPackageArchiveInfo(file.getAbsolutePath(),info("1.9.18-custom",117));
        assertEquals(file,UpdateDownloads.inspect(app).ready);assertTrue(UpdateDownloads.validReady(app,file));
        UpdateDownloads.delivered(app);assertFalse(UpdateDownloads.needsInstall(app));
        UpdateDownloads.requestInstall(app);assertTrue(UpdateDownloads.needsInstall(app));
        pm.setPackageArchiveInfo(file.getAbsolutePath(),info("1.9.19-custom",118));
        assertFalse(UpdateDownloads.validReady(app,file));assertNull(UpdateDownloads.inspect(app).ready);
    }
    private File target() {File file=new File(app.getExternalFilesDir(null),"updates/test.apk");file.getParentFile().mkdirs();return file;}
    private PackageInfo info(String name,int version) {PackageInfo p=new PackageInfo();p.packageName=app.getPackageName();p.versionName=name;p.versionCode=version;p.signatures=new Signature[]{new Signature(new byte[]{1,2,3})};return p;}
}
